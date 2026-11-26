package com.roomplanner.service;

import com.roomplanner.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 가구가 문을 통과하여 방 안으로 진입하는 경로와 충돌(문틀/벽)을 실시간으로 계산하는 시뮬레이션 엔진.
 */
public class PassageSimulationEngine {

    /** 가구 반입 자세 */
    public enum Orientation {
        NORMAL("기본 자세 (서서 반입)"),
        TILT_SIDE("옆으로 눕혀서 반입"),
        TILT_FRONT("앞뒤로 눕혀서 반입"),
        ROTATE_VERTICAL("세워서 반입 (단면 최소화)");

        public final String label;
        Orientation(String label) { this.label = label; }
    }

    /** 시뮬레이션 특정 프레임의 가구 상태 */
    public record StepState(
            double progress,       // 0.0 ~ 1.0
            Point2D position,
            double rotationDeg,
            List<Point2D> corners,
            boolean collided,
            String statusMessage,
            double leftClearanceMm,
            double rightClearanceMm
    ) {}

    private final Plan plan;
    private final Furniture furniture;
    private final Opening door;
    private final Wall wall;

    public PassageSimulationEngine(Plan plan, Furniture furniture, Opening door) {
        this.plan = plan;
        this.furniture = furniture;
        this.door = door;
        this.wall = door.wall();
    }

    /** 해당 운반 자세에서의 가구 2D 투영 규격 및 높이 [w, d, h] */
    public double[] getEffectiveDimensions(Orientation orient) {
        double w = furniture.getWidth();
        double d = furniture.getDepth();
        double h = furniture.getHeight();

        return switch (orient) {
            case NORMAL -> new double[]{w, d, h};
            case TILT_SIDE -> new double[]{w, h, d};
            case TILT_FRONT -> new double[]{h, d, w};
            case ROTATE_VERTICAL -> new double[]{Math.min(w, d), Math.min(Math.max(w, d), h), Math.max(w, Math.max(d, h))};
        };
    }

    /** 전체 경로를 0%~100% (총 steps개) 프레임으로 미리 계산하여 반환 */
    public List<StepState> computeSimulation(Orientation orient, int totalSteps) {
        List<StepState> steps = new ArrayList<>(totalSteps);

        double[] dims = getEffectiveDimensions(orient);
        double effW = dims[0];
        double effD = dims[1];
        double effH = dims[2];

        // 1. 문의 위치 및 법선 벡터 계산
        double doorLen = door.width();
        double doorOffset = door.offset();
        Point2D pLeft = wall.pointAt(doorOffset);
        Point2D pRight = wall.pointAt(doorOffset + doorLen);
        Point2D pDoorCenter = wall.pointAt(doorOffset + doorLen / 2.0);

        double wallAngle = wall.angle();
        // 벽에 수직인 법선 벡터 (문 밖/문 안 판별)
        double normX = -Math.sin(wallAngle);
        double normY = Math.cos(wallAngle);

        // 가구의 목표 위치 (방 안 기존 위치)
        Point2D targetPos = new Point2D(furniture.getX(), furniture.getY());

        // 타겟 위치가 문 중심 기준으로 법선 벡터의 어느 쪽에 있는지 확인하여 문 안/문 밖 방향 결정
        double dotTarget = (targetPos.x() - pDoorCenter.x()) * normX + (targetPos.y() - pDoorCenter.y()) * normY;
        if (dotTarget < 0) {
            normX = -normX;
            normY = -normY;
        }

        // 문 밖 진입 출발 지점 (문 중심에서 법선 반대 방향으로 1600mm)
        double approachDist = Math.max(1600, effD * 1.5);
        Point2D startPos = new Point2D(pDoorCenter.x() - normX * approachDist, pDoorCenter.y() - normY * approachDist);

        // 문 안 도착 지점 (방 안 1600mm 지점 또는 원래 가구 위치 방향)
        Point2D insidePos = new Point2D(pDoorCenter.x() + normX * 1200, pDoorCenter.y() + normY * 1200);

        // 문을 통과할 때의 정렬 각도 (가구의 좁은 쪽이 문 폭과 일치하도록)
        double passAngleDeg = Math.toDegrees(Math.atan2(normY, normX)) - 90;
        if (effW > effD) {
            passAngleDeg += 90;
        }

        // 벽 기둥 사각형들 (좌측 벽, 우측 벽)
        List<List<Point2D>> wallPolys = buildWallObstacles();

        // 2. 전체 프레임 보간
        boolean hasCollidedEver = false;
        for (int i = 0; i <= totalSteps; i++) {
            double t = (double) i / totalSteps;

            Point2D currentPos;
            double currentRot;

            if (t <= 0.5) {
                // 문 밖 -> 문틀 통과 (t: 0.0 ~ 0.5)
                double subT = t / 0.5;
                currentPos = lerp(startPos, pDoorCenter, subT);
                currentRot = passAngleDeg;
            } else if (t <= 0.75) {
                // 문틀 통과 -> 문 안쪽 진입 (t: 0.5 ~ 0.75)
                double subT = (t - 0.5) / 0.25;
                currentPos = lerp(pDoorCenter, insidePos, subT);
                currentRot = lerpAngle(passAngleDeg, (passAngleDeg + furniture.getRotation()) / 2.0, subT);
            } else {
                // 문 안쪽 -> 최종 위치로 코너링 안착 (t: 0.75 ~ 1.0)
                double subT = (t - 0.75) / 0.25;
                currentPos = lerp(insidePos, targetPos, subT);
                currentRot = lerpAngle((passAngleDeg + furniture.getRotation()) / 2.0, furniture.getRotation(), subT);
            }

            // 가구 폴리곤 계산
            List<Point2D> corners = computeCorners(currentPos, effW, effD, currentRot);

            // 문틀 좌/우 여유 간격 계산
            double leftClearance = distancePointToPolygon(pLeft, corners);
            double rightClearance = distancePointToPolygon(pRight, corners);

            // 충돌 검사
            boolean collided = false;
            String msg = "🟢 정상 진입 중";

            // 높이 검사 (가구 운반 높이가 문 높이 2100mm를 초과하는지)
            if (effH > door.height()) {
                collided = true;
                msg = String.format("🔴 문 높이 초과 (가구 높이 %.0fcm > 문 높이 %.0fcm)", effH / 10, door.height() / 10);
            } else {
                for (List<Point2D> wallPoly : wallPolys) {
                    if (Geometry.overlap(corners, wallPoly)) {
                        collided = true;
                        msg = "🔴 문틀/벽 충돌 발생! (통과 불가)";
                        break;
                    }
                }
            }

            if (collided) hasCollidedEver = true;

            if (!collided) {
                if (t >= 0.45 && t <= 0.55) {
                    double minClearance = Math.min(leftClearance, rightClearance);
                    if (minClearance < 30) {
                        msg = String.format("🟡 주의! 문틀 여유 부족 (여유 %.1fcm)", minClearance / 10.0);
                    } else {
                        msg = String.format("🟢 문틀 안전 통과 (여유 %.1fcm)", minClearance / 10.0);
                    }
                } else if (t >= 0.98) {
                    msg = "🎉 반입 완료! 목표 위치에 안전하게 안착했습니다.";
                }
            }

            steps.add(new StepState(t, currentPos, currentRot, corners, collided, msg, leftClearance, rightClearance));
        }

        return steps;
    }

    /** 벽을 문 좌측/우측 조각으로 분할하여 충돌 장애물 폴리곤 생성 */
    private List<List<Point2D>> buildWallObstacles() {
        List<List<Point2D>> polys = new ArrayList<>();
        double thickness = wall.thickness();

        // 1. 문이 있는 벽의 좌측 조각
        if (door.offset() > 10) {
            Point2D p0 = wall.pointAt(0);
            Point2D p1 = wall.pointAt(door.offset());
            polys.add(Geometry.thickLine(p0, p1, thickness));
        }

        // 2. 문이 있는 벽의 우측 조각
        if (wall.length() - door.endOffset() > 10) {
            Point2D p2 = wall.pointAt(door.endOffset());
            Point2D p3 = wall.pointAt(wall.length());
            polys.add(Geometry.thickLine(p2, p3, thickness));
        }

        // 3. 도면의 다른 벽들도 충돌체로 추가
        for (Wall other : plan.getWalls()) {
            if (other != wall) {
                polys.add(Geometry.thickLine(other.start(), other.end(), other.thickness()));
            }
        }

        return polys;
    }

    private static Point2D lerp(Point2D a, Point2D b, double t) {
        return new Point2D(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
    }

    private static double lerpAngle(double aDeg, double bDeg, double t) {
        double diff = (bDeg - aDeg) % 360;
        if (diff < -180) diff += 360;
        if (diff > 180) diff -= 360;
        return aDeg + diff * t;
    }

    private static List<Point2D> computeCorners(Point2D center, double w, double d, double rotDeg) {
        double rad = Math.toRadians(rotDeg);
        double c = Math.cos(rad), s = Math.sin(rad);
        double hw = w / 2.0, hd = d / 2.0;
        double[][] local = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};

        List<Point2D> out = new ArrayList<>(4);
        for (double[] p : local) {
            out.add(new Point2D(center.x() + p[0] * c - p[1] * s, center.y() + p[0] * s + p[1] * c));
        }
        return out;
    }

    private static double distancePointToPolygon(Point2D pt, List<Point2D> poly) {
        double minDist = Double.MAX_VALUE;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Point2D a = poly.get(i);
            Point2D b = poly.get((i + 1) % n);
            minDist = Math.min(minDist, distancePointToSegment(pt, a, b));
        }
        return minDist;
    }

    private static double distancePointToSegment(Point2D p, Point2D a, Point2D b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return p.distanceTo(a);
        double t = Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        Point2D proj = new Point2D(a.x() + t * dx, a.y() + t * dy);
        return p.distanceTo(proj);
    }
}
