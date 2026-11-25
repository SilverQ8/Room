package com.roomplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** 도면 전체: 벽, 방, 문/창문, 가구의 보관함. 2D 화면과 3D 화면이 모두 이 객체를 바라본다. */
public class Plan {
    private final List<Wall> walls = new ArrayList<>();
    private final List<Room> rooms = new ArrayList<>();
    private final List<Opening> openings = new ArrayList<>();
    private final List<Furniture> furniture = new ArrayList<>();

    public void addWall(Wall w) { walls.add(w); }
    public void addRoom(Room r) { rooms.add(r); }
    public void addOpening(Opening o) { openings.add(o); }
    public void addFurniture(Furniture f) { furniture.add(f); }

    public List<Wall> getWalls() { return List.copyOf(walls); }
    public List<Room> getRooms() { return List.copyOf(rooms); }
    public List<Opening> getOpenings() { return List.copyOf(openings); }
    public List<Furniture> getFurniture() { return List.copyOf(furniture); }

    /** 특정 벽에 속한 개구부를 시작 위치 순으로. */
    public List<Opening> openingsOf(Wall w) {
        List<Opening> list = new ArrayList<>();
        for (Opening o : openings) {
            if (o.wall() == w) list.add(o);
        }
        list.sort(Comparator.comparingDouble(Opening::offset));
        return list;
    }

    // 같은 좌표의 벽이 두 개 있어도 정확히 그 객체만 지우도록 == (동일성)으로 비교한다.
    public void removeWall(Wall w) {
        walls.removeIf(x -> x == w);
        openings.removeIf(o -> o.wall() == w);
    }

    /** 기존 벽을 새 벽(치수/좌표 변경)으로 교체한다. 연결된 개구부도 함께 갱신. */
    public void replaceWall(Wall oldWall, Wall newWall) {
        int idx = -1;
        for (int i = 0; i < walls.size(); i++) {
            if (walls.get(i) == oldWall) { idx = i; break; }
        }
        if (idx >= 0) {
            walls.set(idx, newWall);
            double newLen = newWall.length();
            for (int i = 0; i < openings.size(); i++) {
                Opening o = openings.get(i);
                if (o.wall() == oldWall) {
                    double offset = Math.min(o.offset(), Math.max(0, newLen - o.width()));
                    openings.set(i, new Opening(o.type(), newWall, offset, o.width(), o.sill(), o.height()));
                }
            }
        }
    }

    public void removeRoom(Room r) { rooms.removeIf(x -> x == r); }
    public void removeOpening(Opening o) { openings.removeIf(x -> x == o); }
    public void removeFurniture(Furniture f) { furniture.removeIf(x -> x == f); }

    public boolean isEmpty() {
        return walls.isEmpty() && rooms.isEmpty() && furniture.isEmpty();
    }

    // ------------------------------------------------------------------
    // 충돌 검사
    // ------------------------------------------------------------------

    /** 벽이 실제로 막고 있는 부분(문 자리는 비움)을 사각형들로. 창문은 아래쪽 벽이 남아 있으므로 막힌 것으로 본다. */
    public List<List<Point2D>> wallSolids(Wall w) {
        List<List<Point2D>> out = new ArrayList<>();
        double len = w.length();
        if (len < 1) return out;
        double cur = 0;
        for (Opening o : openingsOf(w)) {
            if (o.type() != Opening.Type.DOOR) continue;
            double a = Math.max(o.offset(), cur), b = Math.min(o.endOffset(), len);
            if (b <= a) continue;
            if (a > cur) out.add(Geometry.thickLine(w.pointAt(cur), w.pointAt(a), w.thickness()));
            cur = b;
        }
        if (cur < len) out.add(Geometry.thickLine(w.pointAt(cur), w.pointAt(len), w.thickness()));
        return out;
    }

    /** 이 가구가 다른 가구나 벽과 겹치는지. */
    public boolean collides(Furniture f) {
        List<Point2D> poly = f.corners();
        for (Furniture other : furniture) {
            if (other != f && Geometry.overlap(poly, other.corners())) return true;
        }
        for (Wall w : walls) {
            for (List<Point2D> solid : wallSolids(w)) {
                if (Geometry.overlap(poly, solid)) return true;
            }
        }
        return false;
    }

    /** 겹쳐 있는 모든 가구 (동일성 기준 집합). */
    public Set<Furniture> collidingFurniture() {
        Set<Furniture> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Furniture f : furniture) {
            if (collides(f)) set.add(f);
        }
        return set;
    }
}
