package com.roomplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 다각형(방 바닥)을 삼각형으로 쪼갠다 — ear clipping 알고리즘.
 * 3D에서 바닥 메쉬(TriangleMesh)를 만들 때 사용한다. 자기 교차가 없는 단순 다각형(오목 포함)을 지원한다.
 */
public final class Triangulator {
    private Triangulator() {}

    /** 반환: 각 원소는 꼭짓점 인덱스 3개. */
    public static List<int[]> triangulate(List<Point2D> pts) {
        List<int[]> out = new ArrayList<>();
        int n = pts.size();
        if (n < 3) return out;

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        if (signedArea(pts) < 0) Collections.reverse(idx);   // 항상 반시계 방향으로 맞춘다

        int guard = 0;
        while (idx.size() > 3 && guard++ < 10000) {
            boolean cut = false;
            int m = idx.size();
            for (int i = 0; i < m; i++) {
                int a = idx.get((i + m - 1) % m), b = idx.get(i), c = idx.get((i + 1) % m);
                Point2D pa = pts.get(a), pb = pts.get(b), pc = pts.get(c);
                if (cross(pa, pb, pc) <= 1e-9) continue;          // 볼록한 꼭짓점만 귀(ear) 후보
                boolean blocked = false;
                for (int j : idx) {
                    if (j == a || j == b || j == c) continue;
                    Point2D p = pts.get(j);
                    if (p.equals(pa) || p.equals(pb) || p.equals(pc)) continue;
                    if (inTriangle(p, pa, pb, pc)) { blocked = true; break; }
                }
                if (blocked) continue;
                out.add(new int[]{a, b, c});
                idx.remove(i);
                cut = true;
                break;
            }
            if (!cut) {
                // 일직선 위의 꼭짓점이 막고 있을 수 있으니 하나 제거해 보고, 없으면 포기
                int collinear = -1;
                for (int i = 0; i < m; i++) {
                    Point2D pa = pts.get(idx.get((i + m - 1) % m));
                    Point2D pb = pts.get(idx.get(i));
                    Point2D pc = pts.get(idx.get((i + 1) % m));
                    if (Math.abs(cross(pa, pb, pc)) <= 1e-9) { collinear = i; break; }
                }
                if (collinear < 0) break;
                idx.remove(collinear);
            }
        }
        if (idx.size() == 3) out.add(new int[]{idx.get(0), idx.get(1), idx.get(2)});
        return out;
    }

    static double signedArea(List<Point2D> pts) {
        double sum = 0;
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            Point2D a = pts.get(i), b = pts.get((i + 1) % n);
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return sum / 2;
    }

    private static double cross(Point2D a, Point2D b, Point2D c) {
        return (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
    }

    private static double side(Point2D a, Point2D b, Point2D p) {
        return (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
    }

    private static boolean inTriangle(Point2D p, Point2D a, Point2D b, Point2D c) {
        return side(a, b, p) >= -1e-9 && side(b, c, p) >= -1e-9 && side(c, a, p) >= -1e-9;
    }
}
