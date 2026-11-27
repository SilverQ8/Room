package com.roomplanner.service;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Opening;

import java.util.ArrayList;
import java.util.List;

/**
 * 가구(3D 직육면체: W×D×H)가 도면의 문(2D 개구부: 문 폭×문 높이)을 통과할 수 있는지
 * 수학적으로 정확히 판정하는 서비스.
 */
public class DoorPassageChecker {

    public enum Status {
        PASS("통과 가능"),
        FAIL("통과 불가");

        public final String label;
        Status(String label) { this.label = label; }
    }

    public record PoseResult(
            String poseName,
            boolean pass,
            double sectionWidthMm,
            double sectionHeightMm,
            double marginWidthMm,
            double marginHeightMm,
            String note
    ) {}

    public record CheckResult(
            Furniture furniture,
            Opening door,
            Status status,
            String summary,
            String recommendation,
            List<PoseResult> poses
    ) {}

    public CheckResult check(Furniture furniture, Opening door) {
        double w = furniture.getWidth();
        double d = furniture.getDepth();
        double h = furniture.getHeight();

        double dw = door.width();
        double dh = door.height();

        List<PoseResult> poses = new ArrayList<>();

        // 1. 기본 자세 (서서 반입): 바닥면(W×D) 유지, 높이 H
        // 문 폭 방향으로는 W 또는 D 중 좁은 쪽을 맞추고, 높이는 H가 문 높이를 넘지 않아야 함
        double normalMinHoriz = Math.min(w, d);
        boolean normalPass = (normalMinHoriz <= dw) && (h <= dh);
        poses.add(new PoseResult(
                "기본 자세 (서서 그대로 반입)",
                normalPass,
                normalMinHoriz, h,
                dw - normalMinHoriz, dh - h,
                normalPass
                        ? String.format("여유 폭 %.1f cm, 높이 여유 %.1f cm", (dw - normalMinHoriz) / 10.0, (dh - h) / 10.0)
                        : buildFailReason(normalMinHoriz, h, dw, dh)
        ));

        // 2. 옆으로 눕혀서 반입: 가구의 높이(H)를 문 폭으로, 바닥의 짧은 변을 높이로 (긴 변은 진행 방향)
        // 또는 바닥의 긴 변을 높이로 세울 수도 있으므로 둘 중 유리한 쪽 판단
        double minBase = Math.min(w, d);
        double maxBase = Math.max(w, d);

        boolean lieFlatPass = (h <= dw && minBase <= dh);
        boolean standLongPass = (h <= dw && maxBase <= dh);
        // 눕혔을 때 문에 걸리지 않는 최선의 단면 선택
        double lieSecW = h;
        double lieSecH = lieFlatPass ? minBase : maxBase;
        boolean liePass = lieFlatPass; // 가장 긴 변(maxBase)을 앞뒤 진행 방향으로 넣는 게 일반적

        // 만약 H를 높이로 두고 minBase를 폭으로 쓰는 건 1번(기본 자세)과 같음.
        // 만약 W 또는 D 중 하나를 진행 방향으로 하고, 나머지 하나를 높이로, H를 폭으로 두는 경우:
        poses.add(new PoseResult(
                "옆으로 눕혀서 반입 (두께 방향을 가로로)",
                liePass,
                lieSecW, lieSecH,
                dw - lieSecW, dh - lieSecH,
                liePass
                        ? String.format("여유 폭 %.1f cm, 높이 여유 %.1f cm", (dw - lieSecW) / 10.0, (dh - lieSecH) / 10.0)
                        : buildFailReason(lieSecW, lieSecH, dw, dh)
        ));

        // 3. 세워서 반입 (가장 긴 변을 수직 높이로 세우고, 나머지 두 변을 문 폭에 맞추는 경우)
        double[] sorted = {w, d, h};
        java.util.Arrays.sort(sorted);
        // 가장 작은 두 변: sorted[0], sorted[1], 가장 큰 변: sorted[2]
        // 세워서 반입 시 단면 후보: (sorted[0] × sorted[2]) 또는 (sorted[1] × sorted[2])
        boolean standPassA = (sorted[0] <= dw && sorted[2] <= dh);
        boolean standPassB = (sorted[1] <= dw && sorted[2] <= dh);
        // 사실 가장 이상적인 직각 통과는 가장 긴 변(sorted[2])을 진행 방향으로 두고 (sorted[0] × sorted[1])을 단면으로 넣는 것!
        // 단, 앞뒤 공간이 충분하다면 sorted[0] <= dw && sorted[1] <= dh 일 때 무조건 통과 가능
        boolean bestOrthoPass = (sorted[0] <= dw && sorted[1] <= dh) || (sorted[1] <= dw && sorted[0] <= dh);
        double bestSecW = (sorted[0] <= dw && sorted[1] <= dh) ? sorted[0] : Math.min(sorted[0], sorted[1]);
        double bestSecH = (bestSecW == sorted[0]) ? sorted[1] : sorted[0];
        if (sorted[1] <= dw && sorted[0] <= dh && (dw - sorted[1]) >= 0) {
            // 높이가 더 낮은 쪽을 높이로 두거나 여유가 큰 쪽 선택
            if (dw - sorted[0] < dw - sorted[1]) {
                bestSecW = sorted[1];
                bestSecH = sorted[0];
            }
        }

        // 세워서 반입 (sorted[2]를 높이로 세워야 하는 특이 케이스도 체크)
        boolean verticalPass = standPassA || standPassB;
        double vertW = standPassB ? sorted[1] : sorted[0];
        double vertH = sorted[2];
        if (!bestOrthoPass && verticalPass) {
            poses.add(new PoseResult(
                    "세워서 반입 (긴 변을 수직으로)",
                    true,
                    vertW, vertH,
                    dw - vertW, dh - vertH,
                    String.format("여유 폭 %.1f cm, 높이 여유 %.1f cm", (dw - vertW) / 10.0, (dh - vertH) / 10.0)
            ));
        }

        // 4. 대각선 기울여 반입 (직각으로 안 들어가는 최소 단면 sorted[0] × sorted[1] 을 문 대각선으로 기울여 체크)
        Integer diagAngle = null;
        if (!normalPass && !liePass && !bestOrthoPass) {
            diagAngle = findDiagonalPassAngle(sorted[0], sorted[1], dw, dh);
            poses.add(new PoseResult(
                    "대각선으로 비스듬히 기울여 반입",
                    diagAngle != null,
                    sorted[0], sorted[1],
                    0, 0,
                    diagAngle != null
                            ? String.format("약 %d° 각도로 기울이면 대각선 통과 가능 (문틀 스침 주의)", diagAngle)
                            : String.format("대각선으로 기울여도 문(%.0f×%.0f cm)에 걸림", dw / 10.0, dh / 10.0)
            ));
        }

        // 최종 종합 판정 (통과 가능 / 통과 불가 2단계)
        Status status;
        String summary;
        String recommendation;

        if (normalPass) {
            status = Status.PASS;
            summary = String.format("통과 가능 (폭 여유 %.1f cm)", (dw - normalMinHoriz) / 10.0);
            recommendation = "가구를 세우거나 눕히지 않고 반듯하게 들고 통과할 수 있습니다.";
        } else if (liePass) {
            status = Status.PASS;
            summary = String.format("통과 가능 (옆으로 눕혀서 폭 여유 %.1f cm)", (dw - lieSecW) / 10.0);
            recommendation = String.format("옆으로 눕히면(%.0f cm) 문(%.0f cm)을 여유 있게 통과할 수 있습니다.",
                    lieSecW / 10.0, dw / 10.0);
        } else if (bestOrthoPass) {
            status = Status.PASS;
            summary = String.format("통과 가능 (방향 회전 시 폭 여유 %.1f cm)", (dw - bestSecW) / 10.0);
            recommendation = String.format("가구의 얇은 단면(%.0f×%.0f cm)을 문 방향에 맞추면 통과 가능합니다.",
                    bestSecW / 10.0, bestSecH / 10.0);
        } else if (verticalPass) {
            status = Status.PASS;
            summary = String.format("통과 가능 (세워서 폭 여유 %.1f cm)", (dw - vertW) / 10.0);
            recommendation = "가구를 세워서 이동하면 문을 통과할 수 있습니다.";
        } else if (diagAngle != null) {
            status = Status.PASS;
            summary = String.format("통과 가능 (대각선 %d° 기울여 통과)", diagAngle);
            recommendation = String.format("문틀 대각선 공간을 활용해 약 %d° 기울여 넣을 수 있습니다.", diagAngle);
        } else {
            status = Status.FAIL;
            summary = String.format("통과 불가 (최소 단면 %.0f×%.0f cm > 문 %.0f×%.0f cm)",
                    sorted[0] / 10.0, sorted[1] / 10.0, dw / 10.0, dh / 10.0);
            recommendation = "가구를 어떤 방향으로 돌려도 이 문으로 들어갈 수 없습니다. 분해 배송 또는 창문(사다리차) 반입이 필요합니다.";
        }

        return new CheckResult(furniture, door, status, summary, recommendation, poses);
    }

    private String buildFailReason(double secW, double secH, double dw, double dh) {
        if (secW > dw && secH > dh) {
            return String.format("폭 %.1f cm 초과, 높이 %.1f cm 초과", (secW - dw) / 10.0, (secH - dh) / 10.0);
        } else if (secW > dw) {
            return String.format("문 폭(%.0f cm)보다 %.1f cm 넓음", dw / 10.0, (secW - dw) / 10.0);
        } else {
            return String.format("문 높이(%.0f cm)보다 %.1f cm 높음", dh / 10.0, (secH - dh) / 10.0);
        }
    }

    /**
     * 단면 (a × b) 직사각형을 각도 theta로 기울였을 때 문 단면 (dw × dh) 안에 들어가는 최소/최적 각도 탐색.
     */
    private Integer findDiagonalPassAngle(double a, double b, double dw, double dh) {
        for (int deg = 1; deg <= 89; deg++) {
            double rad = Math.toRadians(deg);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double projW = a * cos + b * sin;
            double projH = a * sin + b * cos;
            // 1mm 여유를 두고 내접하는지 확인
            if (projW <= dw - 1.0 && projH <= dh - 1.0) {
                return deg;
            }
        }
        return null;
    }
}
