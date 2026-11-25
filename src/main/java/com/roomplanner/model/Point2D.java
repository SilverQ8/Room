package com.roomplanner.model;

/** 평면 좌표 (단위: mm). 화면 코드와 무관한 순수 데이터. */
public record Point2D(double x, double y) {

    public double distanceTo(Point2D o) {
        return Math.hypot(x - o.x, y - o.y);
    }

    public Point2D midpoint(Point2D o) {
        return new Point2D((x + o.x) / 2, (y + o.y) / 2);
    }
}
