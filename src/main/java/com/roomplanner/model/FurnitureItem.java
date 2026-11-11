package com.roomplanner.model;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.util.UUID;

public class FurnitureItem {
    public enum Type {
        BED("침대 (Bed)", 160, 200, 70, Color.web("#4A90E2")),
        DESK("책상 (Desk)", 120, 60, 75, Color.web("#8B5A2B")),
        CHAIR("의자 (Chair)", 50, 50, 85, Color.web("#D9534F")),
        WARDROBE("옷장 (Wardrobe)", 100, 60, 190, Color.web("#A0522D")),
        SOFA("소파 (Sofa)", 180, 85, 80, Color.web("#5C6BC0")),
        TV_STAND("TV 및 거실장", 150, 45, 110, Color.web("#37474F"));

        public final String label;
        public final double defaultWidth;
        public final double defaultDepth;
        public final double defaultHeight;
        public final Color defaultColor;

        Type(String label, double defaultWidth, double defaultDepth, double defaultHeight, Color defaultColor) {
            this.label = label;
            this.defaultWidth = defaultWidth;
            this.defaultDepth = defaultDepth;
            this.defaultHeight = defaultHeight;
            this.defaultColor = defaultColor;
        }
    }

    private final String id = UUID.randomUUID().toString();
    private final Type type;
    private String name;

    // 치수 (단위: cm)
    private double width;
    private double depth;
    private double height;

    // 위치 (단위: cm)
    private double x;
    private double y; // 바닥 높이 오프셋 (지상고)
    private double z;

    // 회전 (Y축 각도)
    private double rotationAngle = 0;

    private Color color;

    // 3D 렌더링 노드
    private final Group nodeGroup = new Group();
    private final Translate translate = new Translate();
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

    private boolean selected = false;
    private boolean colliding = false;

    // 하이라이트 박스
    private Box selectionBox;
    private final PhongMaterial normalMaterial = new PhongMaterial();
    private final PhongMaterial selectMaterial = new PhongMaterial(Color.color(1, 0.8, 0, 0.4));
    private final PhongMaterial collideMaterial = new PhongMaterial(Color.color(1, 0.1, 0.1, 0.5));

    public FurnitureItem(Type type, double x, double z) {
        this.type = type;
        this.name = type.label;
        this.width = type.defaultWidth;
        this.depth = type.defaultDepth;
        this.height = type.defaultHeight;
        this.color = type.defaultColor;
        this.x = x;
        this.y = 0;
        this.z = z;

        nodeGroup.getTransforms().addAll(translate, rotateY);
        build3DModel();
        updateTransform();
    }

    public void build3DModel() {
        nodeGroup.getChildren().clear();
        normalMaterial.setDiffuseColor(color);
        normalMaterial.setSpecularColor(Color.color(0.2, 0.2, 0.2));

        switch (type) {
            case BED -> buildBedModel();
            case DESK -> buildDeskModel();
            case CHAIR -> buildChairModel();
            case WARDROBE -> buildWardrobeModel();
            case SOFA -> buildSofaModel();
            case TV_STAND -> buildTVStandModel();
        }

        // 선택/충돌 표시용 투명 바운딩 박스
        selectionBox = new Box(width + 2, height + 2, depth + 2);
        selectionBox.setTranslateY(-height / 2.0);
        selectionBox.setMaterial(selectMaterial);
        selectionBox.setVisible(false);
        nodeGroup.getChildren().add(selectionBox);
    }

    private void buildBedModel() {
        // 프레임
        Box frame = new Box(width, 25, depth);
        frame.setTranslateY(-12.5);
        PhongMaterial woodMat = new PhongMaterial(Color.web("#5C4033"));
        frame.setMaterial(woodMat);

        // 매트리스
        Box mattress = new Box(width - 6, 25, depth - 6);
        mattress.setTranslateY(-37.5);
        mattress.setMaterial(normalMaterial);

        // 헤드보드 (Z축 뒤쪽)
        Box headboard = new Box(width, height, 12);
        headboard.setTranslateY(-height / 2.0);
        headboard.setTranslateZ(-depth / 2.0 + 6);
        headboard.setMaterial(woodMat);

        // 베개 2개
        Box pillow1 = new Box(width * 0.35, 8, depth * 0.2);
        pillow1.setTranslateX(-width * 0.23);
        pillow1.setTranslateY(-54);
        pillow1.setTranslateZ(-depth / 2.0 + 25);
        pillow1.setMaterial(new PhongMaterial(Color.WHITE));

        Box pillow2 = new Box(width * 0.35, 8, depth * 0.2);
        pillow2.setTranslateX(width * 0.23);
        pillow2.setTranslateY(-54);
        pillow2.setTranslateZ(-depth / 2.0 + 25);
        pillow2.setMaterial(new PhongMaterial(Color.WHITE));

        nodeGroup.getChildren().addAll(frame, headboard, mattress, pillow1, pillow2);
    }

    private void buildDeskModel() {
        PhongMaterial topMat = normalMaterial;
        PhongMaterial legMat = new PhongMaterial(Color.web("#333333"));

        // 상판
        Box top = new Box(width, 5, depth);
        top.setTranslateY(-height + 2.5);
        top.setMaterial(topMat);

        // 4개 다리
        double legThick = 4.0;
        double legH = height - 5;
        double legY = -legH / 2.0;
        double xOffset = width / 2.0 - legThick / 2.0 - 2;
        double zOffset = depth / 2.0 - legThick / 2.0 - 2;

        Box leg1 = new Box(legThick, legH, legThick);
        leg1.setTranslateX(-xOffset); leg1.setTranslateY(legY); leg1.setTranslateZ(-zOffset);
        leg1.setMaterial(legMat);

        Box leg2 = new Box(legThick, legH, legThick);
        leg2.setTranslateX(xOffset); leg2.setTranslateY(legY); leg2.setTranslateZ(-zOffset);
        leg2.setMaterial(legMat);

        Box leg3 = new Box(legThick, legH, legThick);
        leg3.setTranslateX(-xOffset); leg3.setTranslateY(legY); leg3.setTranslateZ(zOffset);
        leg3.setMaterial(legMat);

        Box leg4 = new Box(legThick, legH, legThick);
        leg4.setTranslateX(xOffset); leg4.setTranslateY(legY); leg4.setTranslateZ(zOffset);
        leg4.setMaterial(legMat);

        // 노트북/모니터 소품
        Box monitorStand = new Box(16, 2, 12);
        monitorStand.setTranslateY(-height - 1);
        monitorStand.setTranslateZ(-depth * 0.15);
        monitorStand.setMaterial(legMat);

        Box monitorScreen = new Box(45, 28, 2);
        monitorScreen.setTranslateY(-height - 16);
        monitorScreen.setTranslateZ(-depth * 0.15);
        monitorScreen.setMaterial(new PhongMaterial(Color.web("#222222")));

        nodeGroup.getChildren().addAll(top, leg1, leg2, leg3, leg4, monitorStand, monitorScreen);
    }

    private void buildChairModel() {
        PhongMaterial seatMat = normalMaterial;
        PhongMaterial legMat = new PhongMaterial(Color.web("#222222"));

        double seatH = height * 0.52;
        Box seat = new Box(width, 4, depth);
        seat.setTranslateY(-seatH);
        seat.setMaterial(seatMat);

        // 등받이
        Box back = new Box(width, height - seatH, 4);
        back.setTranslateY(-seatH - (height - seatH) / 2.0);
        back.setTranslateZ(-depth / 2.0 + 2);
        back.setMaterial(seatMat);

        // 4개 다리
        double legThick = 3;
        double legH = seatH;
        double legY = -legH / 2.0;
        double xOff = width / 2.0 - legThick;
        double zOff = depth / 2.0 - legThick;

        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                Box leg = new Box(legThick, legH, legThick);
                leg.setTranslateX(dx * xOff);
                leg.setTranslateY(legY);
                leg.setTranslateZ(dz * zOff);
                leg.setMaterial(legMat);
                nodeGroup.getChildren().add(leg);
            }
        }
        nodeGroup.getChildren().addAll(seat, back);
    }

    private void buildWardrobeModel() {
        // 본체
        Box body = new Box(width, height, depth);
        body.setTranslateY(-height / 2.0);
        body.setMaterial(normalMaterial);

        // 손잡이
        Box handle = new Box(2, 20, 3);
        handle.setTranslateX(5);
        handle.setTranslateY(-height / 2.0);
        handle.setTranslateZ(depth / 2.0 + 1.5);
        handle.setMaterial(new PhongMaterial(Color.SILVER));

        Box handle2 = new Box(2, 20, 3);
        handle2.setTranslateX(-5);
        handle2.setTranslateY(-height / 2.0);
        handle2.setTranslateZ(depth / 2.0 + 1.5);
        handle2.setMaterial(new PhongMaterial(Color.SILVER));

        nodeGroup.getChildren().addAll(body, handle, handle2);
    }

    private void buildSofaModel() {
        PhongMaterial mat = normalMaterial;

        // 시트 쿠션
        Box seat = new Box(width, 35, depth);
        seat.setTranslateY(-17.5);
        seat.setMaterial(mat);

        // 등받이
        Box back = new Box(width, height, 20);
        back.setTranslateY(-height / 2.0);
        back.setTranslateZ(-depth / 2.0 + 10);
        back.setMaterial(mat);

        // 양쪽 팔걸이
        double armW = 16;
        Box armLeft = new Box(armW, height * 0.7, depth);
        armLeft.setTranslateX(-width / 2.0 + armW / 2.0);
        armLeft.setTranslateY(-height * 0.35);
        armLeft.setMaterial(mat);

        Box armRight = new Box(armW, height * 0.7, depth);
        armRight.setTranslateX(width / 2.0 - armW / 2.0);
        armRight.setTranslateY(-height * 0.35);
        armRight.setMaterial(mat);

        nodeGroup.getChildren().addAll(seat, back, armLeft, armRight);
    }

    private void buildTVStandModel() {
        // 테이블
        Box table = new Box(width, 40, depth);
        table.setTranslateY(-20);
        table.setMaterial(normalMaterial);

        // TV 받침대
        Box tvStand = new Box(30, 3, 18);
        tvStand.setTranslateY(-41.5);
        tvStand.setMaterial(new PhongMaterial(Color.BLACK));

        // TV 봉
        Box tvPole = new Box(6, 12, 6);
        tvPole.setTranslateY(-49);
        tvPole.setMaterial(new PhongMaterial(Color.DARKGRAY));

        // TV 스크린 (대화면)
        Box tv = new Box(width * 0.75, height - 55, 4);
        tv.setTranslateY(-40 - (height - 55) / 2.0 - 10);
        tv.setMaterial(new PhongMaterial(Color.web("#151515")));

        nodeGroup.getChildren().addAll(table, tvStand, tvPole, tv);
    }

    public void updateTransform() {
        translate.setX(x);
        translate.setY(-y); // Y는 위쪽이 음수
        translate.setZ(z);
        rotateY.setAngle(rotationAngle);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        updateHighlightState();
    }

    public void setColliding(boolean colliding) {
        this.colliding = colliding;
        updateHighlightState();
    }

    private void updateHighlightState() {
        if (colliding) {
            selectionBox.setMaterial(collideMaterial);
            selectionBox.setVisible(true);
        } else if (selected) {
            selectionBox.setMaterial(selectMaterial);
            selectionBox.setVisible(true);
        } else {
            selectionBox.setVisible(false);
        }
    }

    public Bounds getEffectiveBounds() {
        // 회전 각도를 고려한 대략적인 축 정렬 바운딩 계산 (충돌용)
        double rad = Math.toRadians(rotationAngle);
        double cos = Math.abs(Math.cos(rad));
        double sin = Math.abs(Math.sin(rad));
        double effW = width * cos + depth * sin;
        double effD = width * sin + depth * cos;

        return new BoundingBox(
                x - effW / 2.0,
                y,
                z - effD / 2.0,
                effW,
                height,
                effD
        );
    }

    public void rotate90() {
        this.rotationAngle = (this.rotationAngle + 90) % 360;
        updateTransform();
    }

    // Getters and Setters
    public String getId() { return id; }
    public Type getType() { return type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; build3DModel(); }
    public double getDepth() { return depth; }
    public void setDepth(double depth) { this.depth = depth; build3DModel(); }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; build3DModel(); }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; updateTransform(); }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; updateTransform(); }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; updateTransform(); }
    public double getRotationAngle() { return rotationAngle; }
    public void setRotationAngle(double angle) { this.rotationAngle = angle; updateTransform(); }
    public Color getColor() { return color; }
    public void setColor(Color color) {
        this.color = color;
        build3DModel();
    }
    public Group getNodeGroup() { return nodeGroup; }
    public boolean isSelected() { return selected; }
    public boolean isColliding() { return colliding; }
}

