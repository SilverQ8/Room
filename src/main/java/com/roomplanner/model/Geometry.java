package com.roomplanner.model;

import java.util.List;

/** 도형 겹침 판정 등 기하 도우미. */
public final class Geometry {
    private Geometry() {}

    /** 맞닿기만 한 것(겹침 깊이 1mm 이하)은 겹침으로 보지 않는다. */
    private static final double TOUCH_TOLERANCE = 1.0;

    /** 두 볼록 다각형이 겹치는지 — 분리축 정리(SAT). */
    public static boolean overlap(List<Point2D> a, List<Point2D> b) {
        return !separated(a, b) && !separated(b, a);
    }

    /** a의 각 변의 수직 방향(축)에 투영했을 때 a와 b가 떨어져 있는 축이 하나라도 있는지. */
    private static boolean separated(List<Point2D> a, List<Point2D> b) {
        int n = a.size();
        for (int i = 0; i < n; i++) {
            Point2D p = a.get(i), q = a.get((i + 1) % n);
            double nx = -(q.y() - p.y()), ny = q.x() - p.x();
            double len = Math.hypot(nx, ny);
            if (len == 0) continue;
            nx /= len;
            ny /= len;

            double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE;
            for (Point2D pt : a) {
                double d = pt.x() * nx + pt.y() * ny;
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            double minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
            for (Point2D pt : b) {
                double d = pt.x() * nx + pt.y() * ny;
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            if (maxA <= minB + TOUCH_TOLERANCE || maxB <= minA + TOUCH_TOLERANCE) return true;
        }
        return false;
    }

    /** 선분 p–q 를 두께 t 의 사각형(네 꼭짓점)으로. */
    public static List<Point2D> thickLine(Point2D p, Point2D q, double t) {
        double dx = q.x() - p.x(), dy = q.y() - p.y();
        double len = Math.hypot(dx, dy);
        double nx = len == 0 ? 0 : -dy / len * t / 2;
        double ny = len == 0 ? 0 : dx / len * t / 2;
        return List.of(
                new Point2D(p.x() + nx, p.y() + ny), new Point2D(q.x() + nx, q.y() + ny),
                new Point2D(q.x() - nx, q.y() - ny), new Point2D(p.x() - nx, p.y() - ny));
    }
}
