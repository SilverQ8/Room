package com.roomplanner.model;

/**
 * 벽에 뚫린 문/창문.
 * offset: 벽 시작점에서 개구부 시작까지의 거리, width: 폭,
 * sill: 바닥에서 개구부 아래쪽까지의 높이, height: 개구부 높이.
 */
public record Opening(Type type, Wall wall, double offset, double width, double sill, double height) {

    public enum Type { DOOR, WINDOW }

    public double endOffset() {
        return offset + width;
    }

    /** 벽의 centerAlong 위치를 중심으로 기본 크기의 개구부를 만든다. 벽이 너무 짧으면 null. */
    public static Opening place(Type type, Wall wall, double centerAlong) {
        double len = wall.length();
        boolean door = type == Type.DOOR;
        double width = door ? 900 : 1200;
        if (len < width + 200) width = len - 200;
        if (width < 300) return null;
        double offset = Math.max(100, Math.min(len - width - 100, centerAlong - width / 2));
        return new Opening(type, wall, offset, width, door ? 0 : 900, door ? 2100 : 1200);
    }

    /** 같은 벽에서 구간이 겹치는지. */
    public boolean overlaps(Opening o) {
        return o.wall == wall && offset < o.endOffset() && o.offset < endOffset();
    }
}
