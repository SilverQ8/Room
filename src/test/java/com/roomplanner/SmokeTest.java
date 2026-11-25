package com.roomplanner;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Plan;
import com.roomplanner.model.Wall;
import com.roomplanner.ui.CatalogPanel;
import com.roomplanner.ui.MainFrame;
import com.roomplanner.ui.PlanEditor;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 실제 창을 띄워서 (1) 마우스/키 이벤트로 도면을 그려 보고 (2) 2D/3D 화면을 PNG로 캡처한다.
 * 사용법: java ... com.roomplanner.SmokeTest [출력 폴더]
 */
public class SmokeTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "smoke-out");
        Files.createDirectories(out);

        // 실제 앱(Main)과 같은 룩앤필로 띄운다
        SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            com.roomplanner.ui.UiFonts.install();
        });
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainFrame();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        PlanEditor ed = frame.getEditor();
        Thread.sleep(1500);

        // 1) 예제 도면 2D 캡처
        capture2D(ed, out.resolve("1-2d-sample.png"));

        // 2) 빈 도면에서 마우스로 4m x 3m 사각형 그리기 (뷰: scale 0.1, 원점 오프셋 -1000)
        on(() -> ed.setPlan(new Plan()));
        on(() -> ed.setTool(PlanEditor.Tool.WALL));
        int[][] pts = {{0, 0}, {4000, 0}, {4000, 3000}, {0, 3000}, {0, 0}};
        for (int[] p : pts) click(ed, p[0], p[1]);
        Plan plan = ed.getPlan();
        check("사각형: 벽 4개", plan.getWalls().size() == 4);
        check("사각형: 닫히면 방 1개 자동 생성", plan.getRooms().size() == 1);
        check("사각형: 면적 12m²", plan.getRooms().size() == 1 && Math.abs(plan.getRooms().get(0).area() - 12_000_000) < 1);

        // 3) 숫자 길이 입력: (6000,0) 클릭 → 오른쪽으로 마우스 → 3000 입력 + Enter
        click(ed, 6000, 0);
        move(ed, 8000, 0);
        for (char ch : "3000".toCharArray()) type(ed, ch);
        type(ed, '\n');
        Wall typed = ed.getPlan().getWalls().get(ed.getPlan().getWalls().size() - 1);        check("길이 입력: 5번째 벽 생성", ed.getPlan().getWalls().size() == 5);
        check("길이 입력: 길이 3000", Math.abs(typed.length() - 3000) < 1e-6);
        check("길이 입력: 끝점 (9000,0)", Math.abs(typed.end().x() - 9000) < 1e-6 && Math.abs(typed.end().y()) < 1e-6);

        // 4) 같은 점을 두 번 눌러도 길이 0 벽은 생기지 않는다
        click(ed, 9000, 0);
        check("길이 0 벽 방지", ed.getPlan().getWalls().size() == 5);

        // 5) 실행 취소
        on(ed::undo);
        check("실행 취소: 벽 4개로 복귀", ed.getPlan().getWalls().size() == 4);

        // 6) 선택 도구로 방 선택 후 삭제
        on(() -> ed.setTool(PlanEditor.Tool.SELECT));
        click(ed, 2000, 1500);
        on(ed::deleteSelected);
        check("선택 삭제: 방 삭제", ed.getPlan().getRooms().isEmpty());
        capture2D(ed, out.resolve("2-2d-drawn.png"));

        // 7) 가구: 배치 도구로 침대 2개를 겹치게 놓고, 회전한 침대 1개를 더 놓는다
        on(() -> ed.setPlan(new Plan()));
        on(() -> ed.setFurnitureType(Furniture.Type.BED));
        check("가구 도구로 전환됨", ed.getTool() == PlanEditor.Tool.FURNITURE);
        click(ed, 2000, 2000);
        check("가구 배치: 1개", ed.getPlan().getFurniture().size() == 1);
        click(ed, 2500, 2000);   // 폭 1600이라 겹친다
        check("겹침 감지: 둘 다 표시", ed.getPlan().collidingFurniture().size() == 2);
        on(ed::rotateSelected);   // 가구 배치 중에는 미리보기를 90도 회전
        click(ed, 6000, 2000);
        check("회전한 채로 배치", ed.getPlan().getFurniture().size() == 3
                && ed.getPlan().getFurniture().get(2).getRotation() == 90);
        capture2D(ed, out.resolve("3-2d-furniture-collision.png"));
        on(() -> frame.selectTab(1));
        Thread.sleep(2500);
        ImageIO.write(frame.getViewer().snapshot(), "png", out.resolve("4-3d-collision.png").toFile());
        on(() -> frame.selectTab(0));

        // 8) 선택 도구로 가구 끌기 → 겹침 해소, 실행 취소로 원위치
        on(() -> ed.setTool(PlanEditor.Tool.SELECT));
        drag(ed, 2500, 2000, 4000, 2000);
        check("끌기: 두 번째 침대가 (4000,2000)으로 이동", ed.getPlan().getFurniture().get(1).getX() == 4000);
        check("끌기: 겹침 해소", ed.getPlan().collidingFurniture().isEmpty());
        on(ed::undo);
        check("끌기 실행 취소: (2500,2000)으로 복귀", ed.getPlan().getFurniture().get(1).getX() == 2500);
        click(ed, 2500, 2000);
        on(ed::deleteSelected);
        check("가구 삭제", ed.getPlan().getFurniture().size() == 2);

        // 8b) 가구 카탈로그(왼쪽 패널): 클릭 → 화면 중앙에 바로 추가, Shift+클릭 → 위치 지정 모드
        on(() -> ed.setPlan(new Plan()));
        CatalogPanel catalog = frame.getCatalog();
        clickCard(catalog, Furniture.Type.SOFA, false);
        check("카탈로그: 클릭하면 바로 추가", ed.getPlan().getFurniture().size() == 1
                && ed.getPlan().getFurniture().get(0).getType() == Furniture.Type.SOFA);
        check("카탈로그: 추가 직후 선택 도구", ed.getTool() == PlanEditor.Tool.SELECT);
        double centerX = -1000 + ed.getWidth() / 2.0 / 0.1, centerY = -1000 + ed.getHeight() / 2.0 / 0.1;
        Furniture first = ed.getPlan().getFurniture().get(0);
        check("카탈로그: 화면 중앙에 추가됨", Math.abs(first.getX() - centerX) <= 100 && Math.abs(first.getY() - centerY) <= 100);
        clickCard(catalog, Furniture.Type.SOFA, false);
        clickCard(catalog, Furniture.Type.SOFA, false);
        clickCard(catalog, Furniture.Type.BED, false);
        java.util.Set<String> spots = new java.util.HashSet<>();
        for (Furniture f : ed.getPlan().getFurniture()) spots.add(f.getX() + "," + f.getY());
        check("카탈로그: 여러 번 눌러도 서로 다른 자리", ed.getPlan().getFurniture().size() == 4 && spots.size() == 4);
        check("카탈로그: 빈 자리를 찾아 겹치지 않음", ed.getPlan().collidingFurniture().isEmpty());
        clickCard(catalog, Furniture.Type.BED, true);
        check("카탈로그: Shift+클릭 → 가구 배치 모드", ed.getTool() == PlanEditor.Tool.FURNITURE
                && ed.getFurnitureType() == Furniture.Type.BED && catalog.getActive() == Furniture.Type.BED);
        on(() -> ed.setTool(PlanEditor.Tool.SELECT));
        check("카탈로그: 배치 모드가 끝나면 강조 해제", catalog.getActive() == null);

        // 8c) 카탈로그가 보이는 창 전체 캡처 (예제 도면 + 카탈로그에서 추가한 가구)
        on(() -> ed.setPlan(com.roomplanner.model.SamplePlan.create()));
        clickCard(catalog, Furniture.Type.CHAIR, false);
        clickCard(catalog, Furniture.Type.DESK, false);
        clickCard(catalog, Furniture.Type.BED, true);   // 강조 표시도 보이도록 배치 모드로 남겨 둔다
        move(ed, 3000, 800);
        captureFrame(frame, out.resolve("7-frame-with-catalog.png"));
        on(() -> ed.setTool(PlanEditor.Tool.SELECT));

        // 9) 예제 도면(가구 포함) 3D 캡처
        on(() -> ed.setPlan(com.roomplanner.model.SamplePlan.create()));
        capture2D(ed, out.resolve("5-2d-sample-furniture.png"));
        on(() -> frame.selectTab(1));
        Thread.sleep(500);
        frame.getViewer().rebuild(ed.getPlan());
        Thread.sleep(2500);
        BufferedImage img3d = frame.getViewer().snapshot();
        ImageIO.write(img3d, "png", out.resolve("6-3d-sample-furniture.png").toFile());
        check("3D 캡처 크기 > 0", img3d.getWidth() > 0 && img3d.getHeight() > 0);

        System.out.println(failures == 0 ? "\n화면 검사 통과 (캡처: " + out.toAbsolutePath() + ")" : "\n실패 " + failures + "건");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- 도우미 ----

    private static void on(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static int px(int world) { return (int) Math.round((world + 1000) * 0.1); }

    private static void move(PlanEditor ed, int wx, int wy) throws Exception {
        on(() -> ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                px(wx), px(wy), 0, false, MouseEvent.NOBUTTON)));
    }

    private static void click(PlanEditor ed, int wx, int wy) throws Exception {
        move(ed, wx, wy);
        on(() -> {
            ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    MouseEvent.BUTTON1_DOWN_MASK, px(wx), px(wy), 1, false, MouseEvent.BUTTON1));
            ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                    0, px(wx), px(wy), 1, false, MouseEvent.BUTTON1));
        });
    }

    /** 왼쪽 버튼으로 (fromX,fromY)를 눌러 (toX,toY)까지 끈 뒤 놓는다. */
    private static void drag(PlanEditor ed, int fromX, int fromY, int toX, int toY) throws Exception {
        move(ed, fromX, fromY);
        on(() -> ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, px(fromX), px(fromY), 1, false, MouseEvent.BUTTON1)));
        for (int i = 1; i <= 5; i++) {
            int x = fromX + (toX - fromX) * i / 5, y = fromY + (toY - fromY) * i / 5;
            on(() -> ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                    MouseEvent.BUTTON1_DOWN_MASK, px(x), px(y), 0, false, MouseEvent.BUTTON1)));
        }
        on(() -> ed.dispatchEvent(new MouseEvent(ed, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, px(toX), px(toY), 1, false, MouseEvent.BUTTON1)));
    }

    private static void type(PlanEditor ed, char c) throws Exception {
        on(() -> ed.dispatchEvent(new KeyEvent(ed, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                KeyEvent.VK_UNDEFINED, c)));
    }

    private static void capture2D(PlanEditor ed, Path file) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        on(() -> {
            img[0] = new BufferedImage(ed.getWidth(), ed.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img[0].createGraphics();
            ed.paint(g);
            g.dispose();
        });
        ImageIO.write(img[0], "png", file.toFile());
    }

    /** 카탈로그 카드를 실제 마우스처럼 눌렀다 뗀다. */
    private static void clickCard(CatalogPanel catalog, Furniture.Type type, boolean shift) throws Exception {
        java.awt.Component card = catalog.cardOf(type);
        on(() -> card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                shift ? MouseEvent.SHIFT_DOWN_MASK : 0, 10, 10, 1, false, MouseEvent.BUTTON1)));
    }

    /** 창 전체(툴바, 카탈로그, 탭, 상태줄)를 캡처. */
    private static void captureFrame(MainFrame frame, Path file) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        on(() -> {
            java.awt.Container c = frame.getContentPane();
            img[0] = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img[0].createGraphics();
            c.paint(g);
            g.dispose();
        });
        ImageIO.write(img[0], "png", file.toFile());
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) failures++;
    }
}
