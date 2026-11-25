package com.roomplanner.model;

import java.util.ArrayList;
import java.util.List;

/** 닫힌 도형(방): 이름, 채우기 색(RGB int), 꼭짓점 목록. */
public class Room {
    private String name;
    private int fillRgb;
    private final List<Point2D> corners;

    public Room(String name, int fillRgb, List<Point2D> corners) {
        this.name = name;
        this.fillRgb = fillRgb & 0xFFFFFF;
        this.corners = new ArrayList<>(corners);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getFillRgb() { return fillRgb; }
    public void setFillRgb(int rgb) { this.fillRgb = rgb & 0xFFFFFF; }
    public List<Point2D> getCorners() { return List.copyOf(corners); }

    /** 신발끈 공식으로 구한 면적 (mm²). */
    public double area() {
        return Math.abs(signedArea2()) / 2;
    }

    private double signedArea2() {
        double sum = 0;
        int n = corners.size();
        for (int i = 0; i < n; i++) {
            Point2D a = corners.get(i);
            Point2D b = corners.get((i + 1) % n);
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return sum;
    }

    /** 점이 방 안에 있는지 (ray casting). */
    public boolean contains(Point2D p) {
        boolean inside = false;
        int n = corners.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point2D a = corners.get(i);
            Point2D b = corners.get(j);
            if ((a.y() > p.y()) != (b.y() > p.y())
                    && p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 무게중심 (라벨 위치용). */
    public Point2D centroid() {
        double a2 = signedArea2();
        int n = corners.size();
        if (Math.abs(a2) < 1e-6) {
            double sx = 0, sy = 0;
            for (Point2D c : corners) { sx += c.x(); sy += c.y(); }
            return new Point2D(sx / n, sy / n);
        }
        double cx = 0, cy = 0;
        for (int i = 0; i < n; i++) {
            Point2D a = corners.get(i);
            Point2D b = corners.get((i + 1) % n);
            double cross = a.x() * b.y() - b.x() * a.y();
            cx += (a.x() + b.x()) * cross;
            cy += (a.y() + b.y()) * cross;
        }
        return new Point2D(cx / (3 * a2), cy / (3 * a2));
    }
}
