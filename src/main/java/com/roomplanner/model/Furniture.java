package com.roomplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 가구 하나. 위치(x, y)는 바닥면 중심의 도면 좌표(mm), 크기도 mm.
 *
 * 회전 규칙: rotation(도)은 화면에서 시계 방향. 0도일 때 가구의 "앞면"은 도면 위쪽(-y)을 향한다.
 * 3D에서는 Y축 회전 rotation도와 똑같이 대응된다 (벽과 같은 좌표 규칙).
 */
public class Furniture {

    /** 기존 FurnitureItem의 6종 (cm → mm 로 환산). */
    public enum Type {
        BED("침대 (Bed)", 1600, 2000, 700, 0x4A90E2),
        DESK("책상 (Desk)", 1200, 600, 750, 0x8B5A2B),
        CHAIR("의자 (Chair)", 500, 500, 850, 0xD9534F),
        WARDROBE("옷장 (Wardrobe)", 1000, 600, 1900, 0xA0522D),
        SOFA("소파 (Sofa)", 1800, 850, 800, 0x5C6BC0),
        TV_STAND("TV 및 거실장", 1500, 450, 1100, 0x37474F);

        public final String label;
        public final double width, depth, height;
        public final int rgb;

        Type(String label, double width, double depth, double height, int rgb) {
            this.label = label;
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.rgb = rgb;
        }

        /** 콤보박스 등에 라벨이 그대로 보이도록. (저장할 때는 name()을 쓴다) */
        @Override public String toString() { return label; }
    }

    private final Type type;
    private String name;
    private double x, y;
    private double rotation;
    private double width, depth, height;
    private int rgb;

    public Furniture(Type type, double x, double y) {
        this.type = type;
        this.name = type.label;
        this.x = x;
        this.y = y;
        this.width = type.width;
        this.depth = type.depth;
        this.height = type.height;
        this.rgb = type.rgb;
    }

    public Type getType() { return type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getRotation() { return rotation; }
    public void setRotation(double deg) { this.rotation = ((deg % 360) + 360) % 360; }
    public double getWidth() { return width; }
    public void setWidth(double v) { this.width = v; }
    public double getDepth() { return depth; }
    public void setDepth(double v) { this.depth = v; }
    public double getHeight() { return height; }
    public void setHeight(double v) { this.height = v; }
    public int getRgb() { return rgb; }
    public void setRgb(int rgb) { this.rgb = rgb & 0xFFFFFF; }

    public void rotate90() { setRotation(rotation + 90); }

    /**
     * 바닥 면(회전된 사각형)의 네 꼭짓점.
     * 순서: 앞면 왼쪽, 앞면 오른쪽, 뒷면 오른쪽, 뒷면 왼쪽 → corners[0]-[1] 변이 앞면.
     */
    public List<Point2D> corners() {
        double rad = Math.toRadians(rotation);
        double c = Math.cos(rad), s = Math.sin(rad);
        double hw = width / 2, hd = depth / 2;
        double[][] local = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
        List<Point2D> out = new ArrayList<>(4);
        for (double[] p : local) {
            out.add(new Point2D(x + p[0] * c - p[1] * s, y + p[0] * s + p[1] * c));
        }
        return out;
    }

    /** 점이 가구 바닥 면 안에 있는지 (점을 가구 기준 좌표로 되돌려서 비교). */
    public boolean contains(Point2D p) {
        double rad = Math.toRadians(rotation);
        double c = Math.cos(rad), s = Math.sin(rad);
        double dx = p.x() - x, dy = p.y() - y;
        double lx = dx * c + dy * s;
        double ly = -dx * s + dy * c;
        return Math.abs(lx) <= width / 2 && Math.abs(ly) <= depth / 2;
    }
}
