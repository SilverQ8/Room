package com.roomplanner.model;

/** 벽 하나: 시작점, 끝점, 두께, 높이 (모두 mm). */
public record Wall(Point2D start, Point2D end, double thickness, double height) {

    public double length() {
        return start.distanceTo(end);
    }

    /** 벽이 향하는 각도(라디안). */
    public double angle() {
        return Math.atan2(end.y() - start.y(), end.x() - start.x());
    }

    /** 시작점에서 d(mm)만큼 벽을 따라간 위치. */
    public Point2D pointAt(double d) {
        double len = length();
        if (len == 0) return start;
        double t = d / len;
        return new Point2D(start.x() + (end.x() - start.x()) * t,
                           start.y() + (end.y() - start.y()) * t);
    }

    /** 점 p를 벽 위에 투영했을 때 시작점으로부터의 거리 (0 ~ length 로 제한). */
    public double alongOf(Point2D p) {
        double len = length();
        if (len == 0) return 0;
        double ux = (end.x() - start.x()) / len;
        double uy = (end.y() - start.y()) / len;
        double d = (p.x() - start.x()) * ux + (p.y() - start.y()) * uy;
        return Math.max(0, Math.min(len, d));
    }

    /** 점 p에서 벽(선분)까지의 최단 거리. */
    public double distanceTo(Point2D p) {
        return p.distanceTo(pointAt(alongOf(p)));
    }
}
