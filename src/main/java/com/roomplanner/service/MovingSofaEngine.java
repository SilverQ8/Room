package com.roomplanner.service;

import com.roomplanner.model.Point2D;

import java.util.ArrayList;
import java.util.List;

/**
 * 소파 옮기기 문제(Moving Sofa Problem) - 직각 L자 복도에서의
 * 수학적 한계 증명 및 운동학적(Kinematic) 3점 구속 회전 궤적 계산 엔진.
 */
public class MovingSofaEngine {

    /** 수학적 한계 계산 결과 */
    public record ProofReport(
            double corridorWidth1, // 복도 폭 1 (mm)
            double corridorWidth2, // 복도 폭 2 (mm)
            double sofaLength,     // 가구 길이 L (mm)
            double sofaWidth,      // 가구 폭 W (mm)
            double maxAllowedLength, // 수학적 최대 허용 길이 L_max (mm)
            double criticalAngleDeg, // 임계 각도 (도)
            boolean canPass,       // 통과 가능 여부
            String formulaText,    // 수학 공식 텍스트
            String conclusionText  // 판정 결론 텍스트
    ) {}

    /** 각 프레임 상태 */
    public record SofaStep(
            double progress,       // 0.0 ~ 1.0
            double angleDeg,       // 현재 회전각 (0 ~ 90도)
            Point2D center,        // 가구 중심 좌표 (mm)
            List<Point2D> corners, // 가구 4개 꼭짓점
            boolean collided,      // 벽 충돌 여부
            String statusMsg,      // 상태 메시지
            double innerCornerDistMm, // 안쪽 코너와의 최단 간격
            double outerWallDistMm    // 바깥벽과의 최단 간격
    ) {}

    /**
     * Singmaster & Croft 해석 공식 기반 수학적 최대 통과 길이(L_max) 및 증명 리포트 산출
     */
    public static ProofReport analyze(double w1, double w2, double L, double W) {
        // 복도 폭보다 가구 폭이 크면 애초에 진입 불가
        if (W >= w1 || W >= w2) {
            return new ProofReport(w1, w2, L, W, 0, 45.0, false,
                    "가구 폭(W) >= 복도 폭(w) 이므로 진입 자체가 불가능합니다.",
                    "[불가] 가구 폭이 복도보다 큽니다.");
        }

        // L_max(theta) = (w1 - W*sin(theta))/cos(theta) + (w2 - W*cos(theta))/sin(theta)
        // 0 < theta < 90도 사이에서 최솟값 수치 탐색
        double minLmax = Double.MAX_VALUE;
        double critAngle = 45.0;

        for (int i = 1; i < 900; i++) {
            double thetaRad = Math.toRadians(i / 10.0);
            double sin = Math.sin(thetaRad);
            double cos = Math.cos(thetaRad);

            double term1 = (w1 - W * sin) / cos;
            double term2 = (w2 - W * cos) / sin;
            double lmax = term1 + term2;

            if (lmax < minLmax) {
                minLmax = lmax;
                critAngle = i / 10.0;
            }
        }

        boolean canPass = L <= minLmax;
        double margin = Math.abs(minLmax - L);

        String formula = String.format("L_max(θ) = (w1 - W·sinθ)/cosθ + (w2 - W·cosθ)/sinθ");
        String conclusion;
        if (canPass) {
            conclusion = String.format("수학적 통과 가능! (최대 허용: %.1fcm >= 가구: %.1fcm | 여유: %.1fcm)",
                    minLmax / 10.0, L / 10.0, margin / 10.0);
        } else {
            conclusion = String.format("수학적 통과 불가! (가구: %.1fcm > 최대 허용: %.1fcm | %.1fcm 초과)",
                    L / 10.0, minLmax / 10.0, margin / 10.0);
        }

        return new ProofReport(w1, w2, L, W, minLmax, critAngle, canPass, formula, conclusion);
    }

    /**
     * L자 복도를 통과하는 운동학적(Kinematic) 회전 궤적 생성
     * 안쪽 모서리 (0, 0), 바깥 모서리 (w1, -w2)
     */
    public static List<SofaStep> generateTrajectory(double w1, double w2, double L, double W, int totalSteps) {
        List<SofaStep> steps = new ArrayList<>(totalSteps);
        ProofReport report = analyze(w1, w2, L, W);

        // 가구의 안쪽 긴 변이 안쪽 코너 (0,0)에 접하고,
        // 가구의 양 끝이 바깥벽 x=w1, y=-w2 사이를 지나는 궤적
        for (int i = 0; i <= totalSteps; i++) {
            double t = (double) i / totalSteps;
            Point2D center;
            double angleDeg;

            if (t <= 0.25) {
                // 1구간: 세로 복도에서 코너 진입 (theta = 0도 고정, 아래로 이동)
                double subT = t / 0.25;
                angleDeg = 0.0;
                double x = w1 / 2.0;
                double startY = Math.max(L * 1.2, 1800);
                double y = startY - subT * (startY - (L / 2.0 + 50.0));
                center = new Point2D(x, y);
            } else if (t <= 0.75) {
                // 2구간: L자 코너 회전 구간 (theta: 0도 -> 90도 피벗 회전)
                double subT = (t - 0.25) / 0.5;
                angleDeg = subT * 90.0;
                double thetaRad = Math.toRadians(Math.max(0.1, Math.min(89.9, angleDeg)));
                double sin = Math.sin(thetaRad);
                double cos = Math.cos(thetaRad);

                // 안쪽 코너 (0, 0)와 안전 마진 20mm
                double clearance = 20.0;
                // 가구 중심은 안쪽 코너 기준 (W/2 + clearance) 만큼 바깥쪽 법선 방향에 위치
                double cx = (w1 / 2.0) * (1.0 - subT) - (W / 2.0 + clearance) * sin;
                double cy = (W / 2.0 + clearance) * cos - (w2 / 2.0) * subT;

                // 정교한 외벽-내벽 구속 보정
                double offsetX = (w1 / 2.0) * cos;
                double offsetY = -(w2 / 2.0) * sin;
                center = new Point2D(cx + offsetX * 0.4, cy + offsetY * 0.4);
            } else {
                // 3구간: 가로 복도로 빠져나감 (theta = 90도 고정, 왼쪽으로 이동)
                double subT = (t - 0.75) / 0.25;
                angleDeg = 90.0;
                double y = -w2 / 2.0;
                double startX = -(L / 2.0 + 50.0);
                double endX = -Math.max(L * 1.2, 1800);
                double x = startX - subT * (Math.abs(endX) - Math.abs(startX));
                center = new Point2D(x, y);
            }

            // 가구 꼭짓점 계산
            List<Point2D> corners = computeCorners(center, W, L, angleDeg);

            // 벽 충돌 검사 (안쪽 코너 x>0 and y=0, x=0 and y>0 / 바깥벽 x=w1, y=-w2)
            boolean collided = checkLHallwayCollision(corners, w1, w2);

            // 안쪽 코너(0,0)와의 최단 거리
            double innerDist = distancePointToPolygon(new Point2D(0, 0), corners);
            // 바깥벽과의 여유
            double outerDist = computeOuterWallClearance(corners, w1, w2);

            String status;
            if (collided) {
                status = String.format("[충돌] 코너링 실패! 벽에 걸림 (현재 각도: %.1f°)", angleDeg);
            } else {
                if (t >= 0.25 && t <= 0.75) {
                    status = String.format("[회전] 안쪽 모서리 스침 여유: %.1fcm (각도: %.1f°)", innerDist / 10.0, angleDeg);
                } else if (t > 0.95) {
                    status = "[성공] L자 코너 통과 완료! 방 안으로 무사히 안착했습니다.";
                } else {
                    status = "[진입] L자 코너로 접근 중...";
                }
            }

            steps.add(new SofaStep(t, angleDeg, center, corners, collided, status, innerDist, outerDist));
        }

        return steps;
    }

    /** L자 복도 벽과의 충돌 검사 */
    private static boolean checkLHallwayCollision(List<Point2D> corners, double w1, double w2) {
        for (Point2D p : corners) {
            // 바깥벽 1 (오른쪽 x > w1)
            if (p.x() > w1 + 1.0) return true;
            // 바깥벽 2 (아래쪽 y < -w2)
            if (p.y() < -w2 - 1.0) return true;
            // 안쪽 벽 영역 (x < 0 and y > 0)
            if (p.x() < -1.0 && p.y() > 1.0) return true;
        }
        return false;
    }

    private static double computeOuterWallClearance(List<Point2D> corners, double w1, double w2) {
        double minMargin = Double.MAX_VALUE;
        for (Point2D p : corners) {
            minMargin = Math.min(minMargin, w1 - p.x());
            minMargin = Math.min(minMargin, p.y() - (-w2));
        }
        return Math.max(0, minMargin);
    }

    private static List<Point2D> computeCorners(Point2D center, double w, double l, double rotDeg) {
        double rad = Math.toRadians(rotDeg);
        double c = Math.cos(rad), s = Math.sin(rad);
        double hw = w / 2.0, hl = l / 2.0;
        // 회전각 0도일 때: 세로 방향(L이 Y축, W가 X축)
        double[][] local = {{-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl}};

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
