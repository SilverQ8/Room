package com.roomplanner.model;

/**
 * 네이버 쇼핑 검색 결과 및 파싱된 가구 규격 정보.
 */
public class ShoppingProduct {
    private final String title;
    private final String link;
    private final String imageUrl;
    private final int lprice;
    private final String mallName;
    private final String brand;

    // 파싱된 규격 (단위: mm, -1이면 미검출)
    private double widthMm = -1;
    private double depthMm = -1;
    private double heightMm = -1;

    // 근사치 오차 점수 (낮을수록 사용자의 목표 치수에 가까움)
    private double distanceScore = Double.MAX_VALUE;
    private Furniture.Type matchedType;

    public ShoppingProduct(String title, String link, String imageUrl, int lprice, String mallName, String brand) {
        this.title = title;
        this.link = link;
        this.imageUrl = imageUrl;
        this.lprice = lprice;
        this.mallName = mallName;
        this.brand = brand;
    }

    public String getCleanTitle() {
        return title.replaceAll("<[^>]*>", "").replaceAll("&quot;", "\"").replaceAll("&amp;", "&").trim();
    }

    public String getTitle() { return title; }
    public String getLink() { return link; }
    public String getImageUrl() { return imageUrl; }
    public int getLprice() { return lprice; }
    public String getMallName() { return mallName; }
    public String getBrand() { return brand; }

    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }

    public double getDepthMm() { return depthMm; }
    public void setDepthMm(double depthMm) { this.depthMm = depthMm; }

    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }

    public double getDistanceScore() { return distanceScore; }
    public void setDistanceScore(double distanceScore) { this.distanceScore = distanceScore; }

    public Furniture.Type getMatchedType() { return matchedType; }
    public void setMatchedType(Furniture.Type matchedType) { this.matchedType = matchedType; }

    public boolean hasParsedDimensions() {
        return widthMm > 0 && depthMm > 0;
    }

    public String getDimensionString() {
        if (!hasParsedDimensions()) return "규격: 상세 페이지 참조";
        String s = String.format("규격: 가로 %.0fcm × 세로 %.0fcm", widthMm / 10.0, depthMm / 10.0);
        if (heightMm > 0) {
            s += String.format(" × 높이 %.0fcm", heightMm / 10.0);
        }
        return s;
    }
}
