package com.roomplanner.model;

import java.util.List;

/** 처음 실행할 때 볼 수 있는 예제 도면: 거실 + 침실, 문 2개, 창문 4개. */
public final class SamplePlan {
    private SamplePlan() {}

    private static Furniture furniture(Furniture.Type type, double x, double y, double rotation) {
        Furniture f = new Furniture(type, x, y);
        f.setRotation(rotation);
        return f;
    }

    public static Plan create() {
        Plan p = new Plan();
        double t = 150, h = 2400;

        Point2D a = new Point2D(0, 0), b = new Point2D(5000, 0), c = new Point2D(8500, 0);
        Point2D d = new Point2D(8500, 4000), e = new Point2D(5000, 4000), f = new Point2D(0, 4000);

        Wall top1 = new Wall(a, b, t, h);
        Wall top2 = new Wall(b, c, t, h);
        Wall right = new Wall(c, d, t, h);
        Wall bottom2 = new Wall(d, e, t, h);
        Wall bottom1 = new Wall(e, f, t, h);
        Wall left = new Wall(f, a, t, h);
        Wall mid = new Wall(b, e, t, h);
        for (Wall w : List.of(top1, top2, right, bottom2, bottom1, left, mid)) p.addWall(w);

        p.addOpening(Opening.place(Opening.Type.DOOR, mid, 1000));
        p.addOpening(Opening.place(Opening.Type.DOOR, bottom1, 2500));
        p.addOpening(Opening.place(Opening.Type.WINDOW, top1, 2500));
        p.addOpening(Opening.place(Opening.Type.WINDOW, top2, 1750));
        p.addOpening(Opening.place(Opening.Type.WINDOW, left, 3000));
        p.addOpening(Opening.place(Opening.Type.WINDOW, right, 2000));

        p.addRoom(new Room("거실", 0xBDD7EE, List.of(a, b, e, f)));
        p.addRoom(new Room("침실", 0xF4C7C3, List.of(b, c, d, e)));

        // 가구 (앞면은 rotation 0도일 때 도면 위쪽. 90도 = 오른쪽, 180도 = 아래쪽, 270도 = 왼쪽을 향함)
        p.addFurniture(furniture(Furniture.Type.TV_STAND, 300, 2800, 90));    // 거실 왼쪽 벽, 오른쪽을 봄
        p.addFurniture(furniture(Furniture.Type.SOFA, 3500, 2800, 270));      // TV를 마주 봄
        p.addFurniture(furniture(Furniture.Type.BED, 7425, 1700, 270));       // 머리판이 오른쪽 벽
        p.addFurniture(furniture(Furniture.Type.WARDROBE, 5800, 3625, 0));    // 침실 아래쪽 벽, 위를 봄
        p.addFurniture(furniture(Furniture.Type.DESK, 7600, 3625, 0));
        p.addFurniture(furniture(Furniture.Type.CHAIR, 7600, 3050, 180));     // 책상을 바라봄
        return p;
    }
}
