package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Opening;
import com.roomplanner.model.Plan;
import com.roomplanner.model.PlanIO;
import com.roomplanner.model.Point2D;
import com.roomplanner.model.Room;
import com.roomplanner.model.Triangulator;
import com.roomplanner.model.Wall;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 2D 도면(Plan)을 3D로 세워서 보여 주는 JavaFX 뷰어 (Swing 안에 JFXPanel로 포함).
 *
 * 좌표 대응 (JavaFX는 Y가 아래쪽이 +):
 *   도면 x  → 3D X,   도면 y → 3D -Z,   높이 → 3D -Y
 * 도면 y를 -Z로 두면 위에서 내려다본 모습이 2D 도면과 같은 방향(거울상 아님)이 된다.
 */
public class Viewer3D extends JFXPanel {

    private Group world;
    private PerspectiveCamera camera;
    private PointLight light;
    private final Translate pivot = new Translate();
    private final Rotate rotY = new Rotate(20, Rotate.Y_AXIS);
    private final Rotate rotX = new Rotate(-55, Rotate.X_AXIS);   // 앞쪽 벽에 가구가 가려지지 않도록 꽤 높은 시점
    private final Translate camDist = new Translate(0, 0, -9000);
    private Scene scene;
    private double dist = 9000;
    private double lastX, lastY;

    private final PhongMaterial wallMat = material(Color.web("#ece8e0"));
    private final PhongMaterial glassMat = material(Color.web("#7fb8e8", 0.45));
    private final PhongMaterial groundMat = material(Color.web("#9fabb6"));

    public Viewer3D() {
        Platform.setImplicitExit(false);
        Platform.runLater(this::initScene);
    }

    // ------------------------------------------------------------------
    // 공개 API
    // ------------------------------------------------------------------

    /** 현재 도면으로 3D 장면을 다시 만든다. (Swing 스레드에서 호출해도 안전) */
    public void rebuild(Plan plan) {
        String snapshot = PlanIO.toText(plan);   // 스레드 간 안전하게 넘기려고 텍스트 복사본 사용
        Platform.runLater(() -> build(PlanIO.fromText(snapshot)));
    }

    /** 현재 3D 화면을 이미지로 캡처. */
    public BufferedImage snapshot() throws Exception {
        CompletableFuture<BufferedImage> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                WritableImage img = scene.snapshot(null);
                f.complete(SwingFXUtils.fromFXImage(img, null));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(15, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------
    // 장면 초기화 (JavaFX 스레드)
    // ------------------------------------------------------------------

    private void initScene() {
        Group root = new Group();
        world = new Group();

        camera = new PerspectiveCamera(true);
        camera.setNearClip(10);
        camera.setFarClip(500_000);
        camera.setFieldOfView(35);
        // 변환 순서: 카메라를 뒤로 빼고(camDist) → 아래로 기울이고(rotX) → 회전(rotY) → 도면 중심으로 이동(pivot)
        camera.getTransforms().addAll(pivot, rotY, rotX, camDist);

        AmbientLight ambient = new AmbientLight(Color.gray(0.5));
        light = new PointLight(Color.gray(0.42));

        root.getChildren().addAll(world, camera, ambient, light);
        scene = new Scene(root, 900, 600, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.web("#e9eef4"));
        scene.setCamera(camera);

        scene.setOnMousePressed(e -> { lastX = e.getSceneX(); lastY = e.getSceneY(); });
        scene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - lastX, dy = e.getSceneY() - lastY;
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            if (e.isPrimaryButtonDown()) {                       // 회전
                rotY.setAngle(rotY.getAngle() + dx * 0.4);
                rotX.setAngle(Math.max(-89, Math.min(-2, rotX.getAngle() - dy * 0.3)));
            } else if (e.isSecondaryButtonDown() || e.isMiddleButtonDown()) {   // 이동
                double k = dist / 800, yaw = Math.toRadians(rotY.getAngle());
                pivot.setX(pivot.getX() - dx * k * Math.cos(yaw) + dy * k * Math.sin(yaw));
                pivot.setZ(pivot.getZ() + dx * k * Math.sin(yaw) + dy * k * Math.cos(yaw));
            }
        });
        scene.setOnScroll(e -> {                                  // 확대/축소
            dist = Math.max(500, Math.min(400_000, dist * Math.exp(-e.getDeltaY() * 0.002)));
            camDist.setZ(-dist);
        });
        setScene(scene);
    }

    // ------------------------------------------------------------------
    // 도면 → 3D 변환 (JavaFX 스레드)
    // ------------------------------------------------------------------

    private void build(Plan plan) {
        if (world == null) return;
        world.getChildren().clear();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Wall w : plan.getWalls()) {
            for (Point2D p : new Point2D[]{w.start(), w.end()}) {
                minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            }
        }
        if (minX > maxX) { minX = 0; maxX = 5000; minY = 0; maxY = 4000; }

        for (Wall w : plan.getWalls()) addWall(w, plan.openingsOf(w));
        for (Room r : plan.getRooms()) addFloor(r);

        Set<Furniture> colliding = plan.collidingFurniture();
        for (Furniture f : plan.getFurniture()) {
            world.getChildren().add(FurnitureModels.build(f, colliding.contains(f)));
        }

        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        double extent = Math.max(Math.max(maxX - minX, maxY - minY), 3000);

        Box ground = new Box(extent * 14, 10, extent * 14);   // 충분히 크게: 화면 가장자리가 보이지 않도록
        ground.setMaterial(groundMat);
        ground.setTranslateX(cx);
        ground.setTranslateY(12);
        ground.setTranslateZ(-cy);
        world.getChildren().add(ground);

        pivot.setX(cx);
        pivot.setY(-800);
        pivot.setZ(-cy);
        dist = extent * 1.5 + 2000;
        camDist.setZ(-dist);
        light.setTranslateX(cx - extent * 0.3);
        light.setTranslateY(-extent * 2 - 4000);
        light.setTranslateZ(-cy - extent * 0.8);
    }

    /** 벽 하나를 상자(Box) 조각들로 세운다. 문/창문 부분은 비워 둔다. */
    private void addWall(Wall w, List<Opening> openings) {
        double len = w.length();
        if (len < 1) return;
        double h = w.height(), t = w.thickness();
        double ux = (w.end().x() - w.start().x()) / len, uy = (w.end().y() - w.start().y()) / len;
        double deg = Math.toDegrees(w.angle());

        // 조각 = {벽 따라 시작, 끝, 바닥 높이, 윗 높이}
        List<double[]> pieces = new ArrayList<>();
        double cur = 0;
        for (Opening o : openings) {
            double a = Math.max(o.offset(), cur), b = Math.min(o.endOffset(), len);
            if (b <= a) continue;
            if (a > cur) pieces.add(new double[]{cur, a, 0, h});
            double top = Math.min(h, o.sill() + o.height());
            if (o.sill() > 0) pieces.add(new double[]{a, b, 0, Math.min(o.sill(), h)});
            if (top < h) pieces.add(new double[]{a, b, top, h});
            if (o.type() == Opening.Type.WINDOW && top > o.sill()) {
                addBox(w, ux, uy, deg, a, b, o.sill(), top, t * 0.25, glassMat);
            }
            cur = b;
        }
        if (cur < len) pieces.add(new double[]{cur, len, 0, h});

        for (double[] p : pieces) {
            // 벽 양 끝은 두께의 절반만큼 늘려서 모서리가 빈틈없이 만나게 한다 (2D의 CAP_SQUARE와 같은 규칙)
            double a = p[0] - (p[0] <= 1e-6 ? t / 2 : 0);
            double b = p[1] + (p[1] >= len - 1e-6 ? t / 2 : 0);
            addBox(w, ux, uy, deg, a, b, p[2], p[3], t, wallMat);
        }
    }

    private void addBox(Wall w, double ux, double uy, double deg,
                        double a, double b, double y0, double y1, double depth, PhongMaterial mat) {
        double length = b - a, height = y1 - y0;
        if (length <= 0 || height <= 0) return;
        double mid = (a + b) / 2;
        Box box = new Box(length, height, depth);
        box.setMaterial(mat);
        box.setTranslateX(w.start().x() + ux * mid);
        box.setTranslateY(-(y0 + y1) / 2);
        box.setTranslateZ(-(w.start().y() + uy * mid));
        box.setRotationAxis(Rotate.Y_AXIS);
        box.setRotate(deg);
        world.getChildren().add(box);
    }

    /** 방 바닥: 다각형을 삼각형으로 쪼개 TriangleMesh로 만든다. */
    private void addFloor(Room r) {
        List<Point2D> c = r.getCorners();
        List<int[]> tris = Triangulator.triangulate(c);
        if (tris.isEmpty()) return;

        TriangleMesh mesh = new TriangleMesh();
        for (Point2D p : c) {
            mesh.getPoints().addAll((float) p.x(), 0f, (float) -p.y());
        }
        mesh.getTexCoords().addAll(0f, 0f);
        for (int[] t : tris) {
            // 삼각분할 결과는 도면 기준 반시계 방향이다. 3D로 옮기면 y가 -Z가 되어 면의 방향(법선)이
            // 아래쪽을 향하므로, 순서를 뒤집어 위쪽(-Y)을 향하게 해야 조명을 받는다.
            mesh.getFaces().addAll(t[0], 0, t[2], 0, t[1], 0);
        }
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);   // 위/아래 어느 쪽에서 봐도 보이게
        view.setMaterial(material(Color.web(String.format("#%06X", r.getFillRgb()))));
        world.getChildren().add(view);
    }

    private static PhongMaterial material(Color c) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSpecularColor(Color.gray(0.15));
        return m;
    }
}
