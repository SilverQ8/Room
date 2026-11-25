package com.roomplanner;

import com.roomplanner.model.*;

import java.util.List;

/** 화면 없이 모델 로직만 검사한다. 실패하면 종료 코드 1. */
public class ModelCheck {
    private static int failures = 0;

    public static void main(String[] args) {
        // 1단계: 거리/중점/벽 길이·각도
        Point2D a = new Point2D(0, 0), b = new Point2D(4000, 0), c = new Point2D(4000, 3000), d = new Point2D(0, 3000);
        check("거리", near(a.distanceTo(c), 5000));
        check("중점", a.midpoint(c).equals(new Point2D(2000, 1500)));
        Wall w = new Wall(b, c, 150, 2400);
        check("벽 길이", near(w.length(), 3000));
        check("벽 각도 90도", near(Math.toDegrees(w.angle()), 90));
        check("벽까지 거리", near(w.distanceTo(new Point2D(4500, 1500)), 500));

        // 방 면적/내부 판정/무게중심
        Room room = new Room("r", 0xFF0000, List.of(a, b, c, d));
        check("면적 12m²", near(room.area(), 12_000_000));
        check("방 안", room.contains(new Point2D(2000, 1500)));
        check("방 밖", !room.contains(new Point2D(5000, 1500)));
        check("무게중심", near(room.centroid().x(), 2000) && near(room.centroid().y(), 1500));
        Room reversed = new Room("r", 0, List.of(d, c, b, a));
        check("시계/반시계 모두 양수 면적", near(reversed.area(), 12_000_000));

        // 삼각분할: 오목한 L자 (면적 = 삼각형 면적의 합이어야 한다)
        List<Point2D> lShape = List.of(new Point2D(0, 0), new Point2D(4000, 0), new Point2D(4000, 2000),
                new Point2D(2000, 2000), new Point2D(2000, 4000), new Point2D(0, 4000));
        List<int[]> tris = Triangulator.triangulate(lShape);
        double sum = 0;
        for (int[] t : tris) {
            Point2D p = lShape.get(t[0]), q = lShape.get(t[1]), r = lShape.get(t[2]);
            sum += Math.abs((q.x() - p.x()) * (r.y() - p.y()) - (r.x() - p.x()) * (q.y() - p.y())) / 2;
        }
        check("L자 삼각형 4개", tris.size() == 4);
        check("L자 면적 합 12m²", near(sum, 12_000_000));

        // 개구부 배치
        Opening door = Opening.place(Opening.Type.DOOR, new Wall(a, b, 150, 2400), 2000);
        check("문 폭 900", door != null && near(door.width(), 900));
        check("짧은 벽엔 문 없음", Opening.place(Opening.Type.DOOR, new Wall(a, new Point2D(400, 0), 150, 2400), 200) == null);

        // 가구: 바닥 면, 회전, 내부 판정 (침대 1600 x 2000, 중심 (1000,1000))
        Furniture bed = new Furniture(Furniture.Type.BED, 1000, 1000);
        check("가구 꼭짓점 4개", bed.corners().size() == 4);
        check("앞면(0번-1번 꼭짓점)이 도면 위쪽", near(bed.corners().get(0).y(), 0) && near(bed.corners().get(1).y(), 0));
        check("가구 안", bed.contains(new Point2D(1700, 1000)));
        check("가구 밖", !bed.contains(new Point2D(1900, 1000)));
        bed.rotate90();
        check("90도 회전: 가로/세로 바뀜", bed.contains(new Point2D(1900, 1000)) && !bed.contains(new Point2D(1000, 1900)));
        bed.setRotation(450);
        check("회전각은 0~360으로 정규화", near(bed.getRotation(), 90));

        // 충돌: 벽(y=0, 두께 150 → -75~75), 가구끼리, 문 자리
        Plan cp = new Plan();
        Wall cw = new Wall(new Point2D(0, 0), new Point2D(5000, 0), 150, 2400);
        cp.addWall(cw);
        check("벽에 맞닿기만 하면 충돌 아님", !cp.collides(new Furniture(Furniture.Type.DESK, 1000, 375)));
        check("벽을 파고들면 충돌", cp.collides(new Furniture(Furniture.Type.DESK, 1000, 330)));
        Furniture chairInDoorway = new Furniture(Furniture.Type.CHAIR, 2500, 100);
        check("문이 없으면 그 자리 의자는 벽과 충돌", cp.collides(chairInDoorway));
        cp.addOpening(Opening.place(Opening.Type.DOOR, cw, 2500));
        check("문 자리의 의자는 충돌 아님", !cp.collides(chairInDoorway));
        Furniture b1 = new Furniture(Furniture.Type.BED, 2000, 2000);
        Furniture b2 = new Furniture(Furniture.Type.BED, 3000, 2000);   // 1000 떨어짐, 폭 1600 → 겹침
        Furniture b3 = new Furniture(Furniture.Type.BED, 3600, 2000);   // 1600 떨어짐 → 딱 맞닿음
        cp.addFurniture(b1);
        cp.addFurniture(b2);
        check("가구끼리 겹침: 둘 다 표시", cp.collidingFurniture().size() == 2);
        cp.removeFurniture(b2);
        cp.addFurniture(b3);
        check("가구끼리 맞닿음은 충돌 아님", cp.collidingFurniture().isEmpty());

        // 저장/불러오기 왕복
        Plan sample = SamplePlan.create();
        check("예제: 가구 6개", sample.getFurniture().size() == 6);
        check("예제: 겹치는 가구 없음", sample.collidingFurniture().isEmpty());
        String text = PlanIO.toText(sample);
        Plan back = PlanIO.fromText(text);
        check("왕복: 벽 7", back.getWalls().size() == 7);
        check("왕복: 개구부 6", back.getOpenings().size() == 6);
        check("왕복: 방 2", back.getRooms().size() == 2);
        check("왕복: 텍스트 동일", PlanIO.toText(back).equals(text));
        check("왕복: 한글 이름 유지", back.getRooms().get(0).getName().equals("거실"));
        check("왕복: 가구 6개", back.getFurniture().size() == 6);
        Furniture tv = back.getFurniture().get(0);
        check("왕복: 가구 종류/회전/위치 유지", tv.getType() == Furniture.Type.TV_STAND && near(tv.getRotation(), 90)
                && near(tv.getX(), 300) && near(tv.getY(), 2800));

        // 벽 삭제 시 그 벽의 개구부도 사라짐
        Wall mid = back.getWalls().get(6);
        int before = back.getOpenings().size();
        back.removeWall(mid);
        check("벽 삭제 → 문도 삭제", back.getOpenings().size() == before - 1 && back.getWalls().size() == 6);

        System.out.println(failures == 0 ? "\n모든 검사 통과" : "\n실패 " + failures + "건");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) failures++;
    }
}
