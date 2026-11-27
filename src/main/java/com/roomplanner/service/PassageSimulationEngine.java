package com.roomplanner.service;

import com.roomplanner.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 가구 문 통과 시뮬레이션 엔진.
 * 1) 직진 통과 모드 (STRAIGHT)
 * 2) 코너 선진입 비틀기 회전 통과 모드 (WIGGLE / SWIVEL) - 소파/침대를 비스듬히 기울여 모서리부터 넣고 비틀어 빼는 현실적 통과 모션
 */
public class PassageSimulationEngine {

    public enum PassageMode {
        WIGGLE("코너 선진입 비틀기 회전 통과"),
        STRAIGHT("일직선 통과");

        public final String label;
        PassageMode(String label) { this.label = label; }
    }

    public enum Orientation {
        NORMAL("기본 자세 (서서 반입)"),
        TILT_SIDE("옆으로 눕혀서 반입"),
        TILT_FRONT("앞뒤로 눕혀서 반입"),
        ROTATE_VERTICAL("세워서 반입 (단면 최소화)");

        public final String label;
        Orientation(String label) { this.label = label; }
    }

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

    /**
     * 시뮬레이션 프레임 계산 (직진 모드 또는 코너 선진입 비틀기 모드)
     */
    public List<StepState> computeSimulation(PassageMode mode, Orientation orient, int totalSteps) {
        List<StepState> steps = new ArrayList<>(totalSteps);

        double[] dims = getEffectiveDimensions(orient);
        double effW = dims[0];
        double effD = dims[1];
        double effH = dims[2];

        double doorLen = door.width();
        double doorOffset = door.offset();
        Point2D pLeft = wall.pointAt(doorOffset);
        Point2D pRight = wall.pointAt(doorOffset + doorLen);
        Point2D pDoorCenter = wall.pointAt(doorOffset + doorLen / 2.0);

        double wallAngle = wall.angle();
        // 벽 단위벡터 (좌 -> 우)
        double ux = Math.cos(wallAngle);
        double uy = Math.sin(wallAngle);

        // 법선 벡터 (문 통과 방향)
        double normX = -Math.sin(wallAngle);
        double normY = Math.cos(wallAngle);

        // 가구의 목표 위치 방향으로 문 안/밖 결정
        Point2D targetPos = new Point2D(furniture.getX(), furniture.getY());
        double dotTarget = (targetPos.x() - pDoorCenter.x()) * normX + (targetPos.y() - pDoorCenter.y()) * normY;
        if (dotTarget < 0) {
            normX = -normX;
            normY = -normY;
        }

        // 진행 방향 치수
        double travelLength = Math.max(effW, effD);
        double minWidth = Math.min(effW, effD);
        double travelDist = travelLength / 2.0 + 350.0;

        // 문 통과 기본 정렬 각도 (가구의 좁은 쪽이 문틀 폭 방향과 나란함)
        double normAngleDeg = Math.toDegrees(Math.atan2(normY, normX));
        double basePassAngle;
        if (effW <= effD) {
            basePassAngle = normAngleDeg - 90;
        } else {
            basePassAngle = normAngleDeg;
        }

        // 문틀 벽 조각들
        List<List<Point2D>> wallPolys = buildDoorWallObstacles();

        for (int i = 0; i <= totalSteps; i++) {
            double t = (double) i / totalSteps;

            Point2D currentPos;
            double currentRot;

            if (mode == PassageMode.STRAIGHT) {
                // 직진 통과: 문 밖 -> 문틀 -> 문 안 일직선 이동
                double travelOffset = (t - 0.5) * (travelDist * 2.0);
                currentPos = new Point2D(pDoorCenter.x() + normX * travelOffset, pDoorCenter.y() + normY * travelOffset);
                currentRot = basePassAngle;
            } else {
                // 코너 선진입 비틀기(Wiggle) 통과
                // 1) t: 0.0 ~ 0.35 : 비스듬히 기울여 모서리를 문틀 중앙으로 들이밀며 접근
                // 2) t: 0.35 ~ 0.65 : 문틀을 지나며 +tilt -> -tilt 로 비틀기(Swivel)
                // 3) t: 0.65 ~ 1.0 : 문틀을 완전히 빠져나오며 반듯하게 정렬
                double tiltMax = 32.0; // 비틀기 최대 각도 (32도)

                double travelOffset = (t - 0.5) * (travelDist * 2.0);
                double rotOffset;
                double lateralOffset = 0;

                if (t < 0.35) {
                    double subT = t / 0.35;
                    rotOffset = tiltMax; // 비스듬히 기울인 상태로 접근
                    lateralOffset = (1.0 - subT) * (-doorLen * 0.12);
                } else if (t <= 0.65) {
                    // 문틀 통과 중 비틀기 회전: +tiltMax -> -tiltMax 로 부드럽게 S자 회전
                    double subT = (t - 0.35) / 0.30;
                    double smooth = Math.sin((subT - 0.5) * Math.PI); // -1.0 ~ +1.0
                    rotOffset = -smooth * tiltMax;
                    // 가구 중심이 문틀 양쪽을 피해서 S자로 살짝 유영
                    lateralOffset = -Math.cos(subT * Math.PI) * (doorLen * 0.08);
                } else {
                    double subT = (t - 0.65) / 0.35;
                    rotOffset = -tiltMax * (1.0 - subT); // 방 안으로 진입하며 서서히 반듯하게 복귀
                    lateralOffset = (1.0 - subT) * (doorLen * 0.05);
                }

                currentRot = basePassAngle + rotOffset;
                currentPos = new Point2D(
                        pDoorCenter.x() + normX * travelOffset + ux * lateralOffset,
                        pDoorCenter.y() + normY * travelOffset + uy * lateralOffset
                );
            }

            // 가구 폴리곤 계산
            List<Point2D> corners = computeCorners(currentPos, effW, effD, currentRot);

            // 문틀 좌/우 여유 간격 계산
            double leftClearance = distancePointToPolygon(pLeft, corners);
            double rightClearance = distancePointToPolygon(pRight, corners);

            // 충돌 검사
            boolean collided = false;
            String msg = "[진행] 문으로 진입 중...";

            if (effH > door.height()) {
                collided = true;
                msg = String.format("[충돌] 문 높이 초과 (가구 높이 %.0fcm > 문 높이 %.0fcm)", effH / 10, door.height() / 10);
            } else {
                for (List<Point2D> wallPoly : wallPolys) {
                    if (Geometry.overlap(corners, wallPoly)) {
                        collided = true;
                        msg = "[충돌] 문틀에 걸림! (가구가 문보다 큼)";
                        break;
                    }
                }
            }

            if (!collided) {
                if (t >= 0.35 && t <= 0.65) {
                    double minClearance = Math.min(leftClearance, rightClearance);
                    if (mode == PassageMode.WIGGLE) {
                        msg = String.format("[비틀기 통과 중] 문틀 아슬아슬 스침 여유: %.1fcm", minClearance / 10.0);
                    } else {
                        msg = String.format("[직진 통과 중] 문틀 여유: %.1fcm", minClearance / 10.0);
                    }
                } else if (t >= 0.95) {
                    msg = "[성공] 문 통과 완료! 방 안으로 안전하게 들어왔습니다.";
                }
            }

            steps.add(new StepState(t, currentPos, currentRot, corners, collided, msg, leftClearance, rightClearance));
        }

        return steps;
    }

    /** 문이 위치한 벽의 좌/우 문틀 기둥 벽 조각만 장애물로 생성 */
    private List<List<Point2D>> buildDoorWallObstacles() {
        List<List<Point2D>> polys = new ArrayList<>();
        double thickness = wall.thickness();

        if (door.offset() > 5) {
            Point2D p0 = wall.pointAt(0);
            Point2D p1 = wall.pointAt(door.offset());
            polys.add(Geometry.thickLine(p0, p1, thickness));
        }

        if (wall.length() - door.endOffset() > 5) {
            Point2D p2 = wall.pointAt(door.endOffset());
            Point2D p3 = wall.pointAt(wall.length());
            polys.add(Geometry.thickLine(p2, p3, thickness));
        }

        return polys;
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
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return p.distanceTo(a);
        double t = Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        Point2D proj = new Point2D(a.x() + t * dx, a.y() + t * dy);
        return p.distanceTo(proj);
    }
}
