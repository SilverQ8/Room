package com.roomplanner.ui;

import com.roomplanner.model.Furniture;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;

/**
 * 가구 6종의 3D 모델 — 기존 FurnitureItem의 Box 조합을 mm 단위로 옮긴 것.
 *
 * 모델 좌표(가구 기준): 가로 = X, 깊이 = Z, 위 = -Y. 앞면은 +Z 쪽.
 * 바닥은 Y=0 이고 모델은 위(-Y)로 자란다. 도면 y 는 3D -Z 로 대응된다.
 */
final class FurnitureModels {
    private FurnitureModels() {}

    static Group build(Furniture f, boolean colliding) {
        Group g = new Group();
        double w = f.getWidth(), d = f.getDepth(), h = f.getHeight();
        PhongMaterial main = material(Color.web(String.format("#%06X", f.getRgb())));

        switch (f.getType()) {
            case BED -> bed(g, w, d, h, main);
            case DESK -> desk(g, w, d, h, main);
            case CHAIR -> chair(g, w, d, h, main);
            case WARDROBE -> wardrobe(g, w, d, h, main);
            case SOFA -> sofa(g, w, d, h, main);
            case TV_STAND -> tvStand(g, w, d, h, main);
        }

        if (colliding) {   // 겹친 가구는 빨간 반투명 상자로 감싼다
            box(g, w + 20, h + 20, d + 20, 0, -h / 2, 0, material(Color.color(1, 0.1, 0.1, 0.35)));
        }

        g.setTranslateX(f.getX());
        g.setTranslateZ(-f.getY());
        g.getTransforms().add(new Rotate(f.getRotation(), Rotate.Y_AXIS));   // 원점(가구 중심) 기준 회전
        return g;
    }

    private static void bed(Group g, double w, double d, double h, PhongMaterial main) {
        PhongMaterial wood = material(Color.web("#5C4033"));
        box(g, w, 250, d, 0, -125, 0, wood);                                  // 프레임
        box(g, w - 60, 250, d - 60, 0, -375, 0, main);                        // 매트리스
        box(g, w, h, 120, 0, -h / 2, -d / 2 + 60, wood);                      // 헤드보드(뒤쪽)
        PhongMaterial white = material(Color.WHITE);
        box(g, w * 0.35, 80, d * 0.2, -w * 0.23, -540, -d / 2 + 250, white);  // 베개
        box(g, w * 0.35, 80, d * 0.2, w * 0.23, -540, -d / 2 + 250, white);
    }

    private static void desk(Group g, double w, double d, double h, PhongMaterial main) {
        PhongMaterial leg = material(Color.web("#333333"));
        box(g, w, 50, d, 0, -h + 25, 0, main);                                // 상판
        double legH = h - 50, xOff = w / 2 - 40, zOff = d / 2 - 40;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                box(g, 40, legH, 40, sx * xOff, -legH / 2, sz * zOff, leg);   // 다리 4개
            }
        }
        box(g, 160, 20, 120, 0, -h - 10, -d * 0.15, leg);                     // 모니터 받침
        box(g, 450, 280, 20, 0, -h - 160, -d * 0.15, material(Color.web("#222222")));   // 모니터
    }

    private static void chair(Group g, double w, double d, double h, PhongMaterial main) {
        PhongMaterial leg = material(Color.web("#222222"));
        double seatH = h * 0.52;
        box(g, w, 40, d, 0, -seatH, 0, main);                                 // 좌판
        box(g, w, h - seatH, 40, 0, -seatH - (h - seatH) / 2, -d / 2 + 20, main);   // 등받이
        double xOff = w / 2 - 30, zOff = d / 2 - 30;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                box(g, 30, seatH, 30, sx * xOff, -seatH / 2, sz * zOff, leg);
            }
        }
    }

    private static void wardrobe(Group g, double w, double d, double h, PhongMaterial main) {
        PhongMaterial silver = material(Color.SILVER);
        box(g, w, h, d, 0, -h / 2, 0, main);                                  // 본체
        box(g, 20, 200, 30, 50, -h / 2, d / 2 + 15, silver);                  // 손잡이 2개(앞면)
        box(g, 20, 200, 30, -50, -h / 2, d / 2 + 15, silver);
    }

    private static void sofa(Group g, double w, double d, double h, PhongMaterial main) {
        box(g, w, 350, d, 0, -175, 0, main);                                  // 시트
        box(g, w, h, 200, 0, -h / 2, -d / 2 + 100, main);                     // 등받이
        double arm = 160;
        box(g, arm, h * 0.7, d, -w / 2 + arm / 2, -h * 0.35, 0, main);        // 팔걸이 2개
        box(g, arm, h * 0.7, d, w / 2 - arm / 2, -h * 0.35, 0, main);
    }

    private static void tvStand(Group g, double w, double d, double h, PhongMaterial main) {
        box(g, w, 400, d, 0, -200, 0, main);                                  // 거실장
        box(g, 300, 30, 180, 0, -415, 0, material(Color.BLACK));              // TV 받침
        box(g, 60, 120, 60, 0, -490, 0, material(Color.DARKGRAY));            // TV 봉
        double tvH = Math.max(100, h - 550);
        box(g, w * 0.75, tvH, 40, 0, -400 - tvH / 2 - 100, 0, material(Color.web("#151515")));   // TV 화면
    }

    /** 가운데가 (x, y, z)인 상자 하나를 그룹에 추가. */
    private static void box(Group g, double w, double h, double d, double x, double y, double z, PhongMaterial m) {
        Box b = new Box(w, h, d);
        b.setTranslateX(x);
        b.setTranslateY(y);
        b.setTranslateZ(z);
        b.setMaterial(m);
        g.getChildren().add(b);
    }

    private static PhongMaterial material(Color c) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSpecularColor(Color.gray(0.2));
        return m;
    }
}
