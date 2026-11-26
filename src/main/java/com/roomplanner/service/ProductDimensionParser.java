package com.roomplanner.service;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.ShoppingProduct;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쇼핑몰 상품 제목에서 가구 규격(너비, 깊이, 높이)을 정규식으로 추출하고,
 * 사용자의 목표 치수와 비교하여 근사치 점수(오차)를 산출한다.
 */
public class ProductDimensionParser {

    // 1) 3단 치수: 1200x600x720, 1200*600*720, 1200*600*75
    private static final Pattern PATTERN_3D = Pattern.compile(
            "(\\d{2,4})\\s*[xX*×/]\\s*(\\d{2,4})\\s*[xX*×/]\\s*(\\d{2,4})"
    );

    // 2) 2단 치수: 1200x600, 1200*600, 120*60cm
    private static final Pattern PATTERN_2D = Pattern.compile(
            "(\\d{2,4})\\s*(?:cm)?\\s*[xX*×/]\\s*(\\d{2,4})\\s*(?:cm)?"
    );

    // 3) 단일 수치 + 너비/사이즈: 1200, 1400, 1600 (주로 책상/식탁/테이블 등)
    private static final Pattern PATTERN_SINGLE_MM = Pattern.compile(
            "\\b(600|700|800|900|1000|1100|1200|1300|1400|1500|1600|1700|1800|2000)\\b"
    );

    /**
     * 상품 정보에 치수를 파싱하여 채워 넣고, 목표 치수 대비 오차(Distance Score)를 계산한다.
     * @param product 상품
     * @param targetType 선택된 가구 종류
     * @param targetWidthMm 사용자가 원하는 목표 너비 (mm)
     * @param targetDepthMm 사용자가 원하는 목표 깊이 (mm)
     * @param targetHeightMm 사용자가 원하는 목표 높이 (mm, 없으면 <= 0)
     */
    public static void parseAndScore(ShoppingProduct product, Furniture.Type targetType,
                                     double targetWidthMm, double targetDepthMm, double targetHeightMm) {
        product.setMatchedType(targetType);
        String title = product.getCleanTitle();

        double w = product.getWidthMm();
        double d = product.getDepthMm();
        double h = product.getHeightMm();

        // 아직 치수가 파싱되지 않은 경우 제목에서 정규식으로 추출
        if (w <= 0 || d <= 0) {
            // 1. 3D 패턴 검사
            Matcher m3 = PATTERN_3D.matcher(title);
            if (m3.find()) {
                w = normalizeToMm(Double.parseDouble(m3.group(1)));
                d = normalizeToMm(Double.parseDouble(m3.group(2)));
                h = normalizeToMm(Double.parseDouble(m3.group(3)));
            } else {
                // 2. 2D 패턴 검사
                Matcher m2 = PATTERN_2D.matcher(title);
                if (m2.find()) {
                    w = normalizeToMm(Double.parseDouble(m2.group(1)));
                    d = normalizeToMm(Double.parseDouble(m2.group(2)));
                } else {
                    // 3. 침대 표준 규격 키워드 검사
                    if (targetType == Furniture.Type.BED) {
                        if (title.contains("슈퍼싱글") || title.contains("SS")) {
                            w = 1100; d = 2000;
                        } else if (title.contains("싱글") || title.contains(" S ") || title.endsWith(" S")) {
                            w = 1000; d = 2000;
                        } else if (title.contains("퀸") || title.contains(" Q ") || title.endsWith(" Q")) {
                            w = 1500; d = 2000;
                        } else if (title.contains("킹") || title.contains(" K ") || title.endsWith(" K")) {
                            w = 1600; d = 2000;
                        }
                    }

                    // 4. 단일 폭(Width) 수치 검사 (예: 1200 책상)
                    if (w < 0) {
                        Matcher ms = PATTERN_SINGLE_MM.matcher(title);
                        if (ms.find()) {
                            w = Double.parseDouble(ms.group(1));
                            d = (targetDepthMm > 0) ? targetDepthMm : targetType.depth;
                        }
                    }
                }
            }
        }

        // 유효한 규격이면 상품에 세팅 (높이는 실제 명시된 경우에만 양수 유지)
        if (w > 0 && d > 0) {
            product.setWidthMm(w);
            product.setDepthMm(d);
            product.setHeightMm(h > 0 ? h : -1);

            // 오차 점수 (가로/세로 매칭 위주, 높이는 실제 존재할 때만 비교)
            double errW = Math.abs(w - targetWidthMm);
            double errD = Math.abs(d - targetDepthMm);
            double errH = (h > 0 && targetHeightMm > 0) ? Math.abs(h - targetHeightMm) * 0.5 : 0;

            product.setDistanceScore(errW + errD * 1.2 + errH);
        } else {
            // 치수 파싱이 안 된 상품은 -1로 유지하여 '상세 페이지 참조'로 표시되게 함
            product.setWidthMm(-1);
            product.setDepthMm(-1);
            product.setHeightMm(-1);
            product.setDistanceScore(50000.0); // 치수 미확인 페널티
        }
    }

    /** cm 단위인지 mm 단위인지 판별하여 mm 단위로 통일 (예: 120 -> 1200, 1200 -> 1200) */
    private static double normalizeToMm(double val) {
        if (val < 250) { // 250 이하의 수치는 대개 cm 단위 (예: 120cm, 60cm)
            return val * 10.0;
        }
        return val;
    }
}
