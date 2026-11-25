package com.roomplanner.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 도면을 사람이 읽을 수 있는 텍스트(탭 구분)로 저장/복원한다.
 * 실행 취소(undo)도 이 텍스트를 스냅샷으로 사용한다.
 *
 * WALL    sx sy ex ey thickness height
 * OPENING type wallIndex offset width sill height
 * ROOM    rgb name x,y;x,y;...
 * FURNITURE type name x y rotation width depth height rgb
 */
public final class PlanIO {
    private PlanIO() {}

    public static String toText(Plan plan) {
        StringBuilder sb = new StringBuilder("ROOMPLAN 1\n");
        List<Wall> walls = plan.getWalls();
        for (Wall w : walls) {
            sb.append("WALL\t").append(w.start().x()).append('\t').append(w.start().y())
              .append('\t').append(w.end().x()).append('\t').append(w.end().y())
              .append('\t').append(w.thickness()).append('\t').append(w.height()).append('\n');
        }
        for (Opening o : plan.getOpenings()) {
            int idx = indexOfWall(walls, o.wall());
            if (idx < 0) continue;
            sb.append("OPENING\t").append(o.type()).append('\t').append(idx)
              .append('\t').append(o.offset()).append('\t').append(o.width())
              .append('\t').append(o.sill()).append('\t').append(o.height()).append('\n');
        }
        for (Room r : plan.getRooms()) {
            String name = r.getName().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
            sb.append("ROOM\t").append(r.getFillRgb()).append('\t').append(name).append('\t');
            boolean first = true;
            for (Point2D c : r.getCorners()) {
                if (!first) sb.append(';');
                sb.append(c.x()).append(',').append(c.y());
                first = false;
            }
            sb.append('\n');
        }
        for (Furniture item : plan.getFurniture()) {
            String name = item.getName().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
            sb.append("FURNITURE\t").append(item.getType().name()).append('\t').append(name)
              .append('\t').append(item.getX()).append('\t').append(item.getY()).append('\t').append(item.getRotation())
              .append('\t').append(item.getWidth()).append('\t').append(item.getDepth()).append('\t').append(item.getHeight())
              .append('\t').append(item.getRgb()).append('\n');
        }
        return sb.toString();
    }

    public static Plan fromText(String text) {
        Plan plan = new Plan();
        List<Wall> walls = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            try {
                switch (f[0]) {
                    case "WALL" -> {
                        Wall w = new Wall(new Point2D(d(f[1]), d(f[2])), new Point2D(d(f[3]), d(f[4])),
                                d(f[5]), d(f[6]));
                        walls.add(w);
                        plan.addWall(w);
                    }
                    case "OPENING" -> {
                        int idx = Integer.parseInt(f[2]);
                        if (idx >= 0 && idx < walls.size()) {
                            plan.addOpening(new Opening(Opening.Type.valueOf(f[1]), walls.get(idx),
                                    d(f[3]), d(f[4]), d(f[5]), d(f[6])));
                        }
                    }
                    case "ROOM" -> {
                        List<Point2D> corners = new ArrayList<>();
                        for (String pair : f[3].split(";")) {
                            String[] xy = pair.split(",");
                            corners.add(new Point2D(d(xy[0]), d(xy[1])));
                        }
                        plan.addRoom(new Room(f[2], Integer.parseInt(f[1]), corners));
                    }
                    case "FURNITURE" -> {
                        Furniture item = new Furniture(Furniture.Type.valueOf(f[1]), d(f[3]), d(f[4]));
                        item.setName(f[2]);
                        item.setRotation(d(f[5]));
                        item.setWidth(d(f[6]));
                        item.setDepth(d(f[7]));
                        item.setHeight(d(f[8]));
                        item.setRgb(Integer.parseInt(f[9]));
                        plan.addFurniture(item);
                    }
                    default -> { /* 헤더나 알 수 없는 줄은 무시 */ }
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("잘못된 줄: " + line, ex);
            }
        }
        return plan;
    }

    public static void save(Plan plan, Path path) throws IOException {
        Files.writeString(path, toText(plan), StandardCharsets.UTF_8);
    }

    public static Plan load(Path path) throws IOException {
        return fromText(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static double d(String s) { return Double.parseDouble(s); }

    private static int indexOfWall(List<Wall> walls, Wall w) {
        for (int i = 0; i < walls.size(); i++) {
            if (walls.get(i) == w) return i;
        }
        return -1;
    }
}
