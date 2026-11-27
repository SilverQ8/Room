package com.roomplanner.ui;

import com.roomplanner.model.Opening;
import com.roomplanner.model.Plan;
import com.roomplanner.model.Point2D;
import com.roomplanner.model.Wall;
import com.roomplanner.service.PassageSimulationEngine.StepState;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.util.ArrayList;
import java.util.List;

/**
 * 가구의 문 통과 과정을 실시간 3D 입체 화면으로 렌더링하는 JavaFX 뷰어.
 * 마우스 드래그로 카메라 360도 회전, 마우스 휠로 확대/축소 가능.
 */
public class PassageSimulation3DViewer extends JFXPanel {

    private Group world;
    private PerspectiveCamera camera;
    private PointLight light;
    private final Translate pivot = new Translate();
    private final Rotate rotY = new Rotate(30, Rotate.Y_AXIS);
    private final Rotate rotX = new Rotate(-35, Rotate.X_AXIS);
    private final Translate camDist = new Translate(0, 0, -4500);
    private double dist = 4500;
    private double lastX, lastY;

    private final PhongMaterial wallMat = material(Color.web("#d0d7de"));
    private final PhongMaterial groundMat = material(Color.web("#21262d"));
    private final PhongMaterial normalMat = material(Color.web("#58a6ff"));
    private final PhongMaterial collideMat = material(Color.web("#da3633"));
    private final PhongMaterial doorFrameMat = material(Color.web("#d29922"));

    private Box furnitureBox;
    private Rotate furnitureRotY;

    public PassageSimulation3DViewer() {
        Platform.setImplicitExit(false);
        Platform.runLater(this::initScene);
    }

    private void initScene() {
        Group root = new Group();
        world = new Group();

        camera = new PerspectiveCamera(true);
        camera.setNearClip(10);
        camera.setFarClip(500_000);
        camera.setFieldOfView(35);
        camera.getTransforms().addAll(pivot, rotY, rotX, camDist);

        AmbientLight ambient = new AmbientLight(Color.gray(0.55));
        light = new PointLight(Color.gray(0.6));

        root.getChildren().addAll(world, camera, ambient, light);
        Scene scene = new Scene(root, 900, 600, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.web("#161b22"));
        scene.setCamera(camera);

        // 마우스 카메라 조작
        scene.setOnMousePressed(e -> { lastX = e.getSceneX(); lastY = e.getSceneY(); });
        scene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - lastX, dy = e.getSceneY() - lastY;
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            if (e.isPrimaryButtonDown()) { // 회전
                rotY.setAngle(rotY.getAngle() + dx * 0.4);
                rotX.setAngle(Math.max(-89, Math.min(-5, rotX.getAngle() - dy * 0.3)));
            } else if (e.isSecondaryButtonDown() || e.isMiddleButtonDown()) { // 팬 이동
                double k = dist / 800;
                double yaw = Math.toRadians(rotY.getAngle());
                pivot.setX(pivot.getX() - dx * k * Math.cos(yaw) + dy * k * Math.sin(yaw));
                pivot.setZ(pivot.getZ() + dx * k * Math.sin(yaw) + dy * k * Math.cos(yaw));
            }
        });
        scene.setOnScroll(e -> { // 줌
            dist = Math.max(800, Math.min(20_000, dist * Math.exp(-e.getDeltaY() * 0.002)));
            camDist.setZ(-dist);
        });

        setScene(scene);
    }

    /** 3D 환경 구축 (실제 도면의 벽체 및 문틀) */
    public void setup(Plan plan, Opening door) {
        Platform.runLater(() -> buildWorld(plan, door));
    }

    private void buildWorld(Plan plan, Opening door) {
        if (world == null) return;
        world.getChildren().clear();

        Wall doorWall = door.wall();
        Point2D doorCenter = doorWall.pointAt(door.offset() + door.width() / 2.0);

        // 1. 바닥 (Ground)
        Box ground = new Box(16000, 10, 16000);
        ground.setMaterial(groundMat);
        ground.setTranslateX(doorCenter.x());
        ground.setTranslateY(5); // 바닥 표면이 Y=0
        ground.setTranslateZ(-doorCenter.y());
        world.getChildren().add(ground);

        // 2. 도면의 실제 벽체 생성 (문이 뚫린 벽은 개구부 형태로 렌더링)
        for (Wall w : plan.getWalls()) {
            addWall3D(w, plan.openingsOf(w));
        }

        // 3. 문틀 기둥 강조 (좌/우 문틀 기둥)
        Point2D pLeft = doorWall.pointAt(door.offset());
        Point2D pRight = doorWall.pointAt(door.endOffset());
        double doorH = door.height();

        Box leftJamb = new Box(doorWall.thickness() * 1.15, doorH, doorWall.thickness() * 1.15);
        leftJamb.setMaterial(doorFrameMat);
        leftJamb.setTranslateX(pLeft.x());
        leftJamb.setTranslateY(-doorH / 2.0);
        leftJamb.setTranslateZ(-pLeft.y());

        Box rightJamb = new Box(doorWall.thickness() * 1.15, doorH, doorWall.thickness() * 1.15);
        rightJamb.setMaterial(doorFrameMat);
        rightJamb.setTranslateX(pRight.x());
        rightJamb.setTranslateY(-doorH / 2.0);
        rightJamb.setTranslateZ(-pRight.y());

        world.getChildren().addAll(leftJamb, rightJamb);

        // 4. 가구 3D 박스 생성
        furnitureBox = new Box(100, 100, 100);
        furnitureBox.setMaterial(normalMat);
        furnitureRotY = new Rotate(0, Rotate.Y_AXIS);
        furnitureBox.getTransforms().add(furnitureRotY);
        world.getChildren().add(furnitureBox);

        // 카메라 피벗을 문 중심으로 정렬
        pivot.setX(doorCenter.x());
        pivot.setY(-doorH / 2.0);
        pivot.setZ(-doorCenter.y());
        dist = 4000;
        camDist.setZ(-dist);

        light.setTranslateX(doorCenter.x() + 1000);
        light.setTranslateY(-4000);
        light.setTranslateZ(-doorCenter.y() - 2000);
    }

    /** 3D 프레임 갱신 (가구 위치/회전/크기/충돌색상) */
    public void updateStep(StepState state, double[] dims) {
        if (state == null || dims == null) return;
        Platform.runLater(() -> {
            if (furnitureBox == null) return;

            double effW = dims[0];
            double effD = dims[1];
            double effH = dims[2];

            furnitureBox.setWidth(effW);
            furnitureBox.setDepth(effD);
            furnitureBox.setHeight(effH);

            furnitureBox.setTranslateX(state.position().x());
            furnitureBox.setTranslateY(-effH / 2.0); // 바닥(Y=0) 위에 안착
            furnitureBox.setTranslateZ(-state.position().y());

            // 2D rotationDeg (반시계) -> 3D Y축 회전
            furnitureRotY.setAngle(-state.rotationDeg());

            furnitureBox.setMaterial(state.collided() ? collideMat : normalMat);
        });
    }

    private void addWall3D(Wall w, List<Opening> openings) {
        double len = w.length();
        if (len < 1) return;
        double h = w.height(), t = w.thickness();
        double ux = (w.end().x() - w.start().x()) / len, uy = (w.end().y() - w.start().y()) / len;
        double deg = Math.toDegrees(w.angle());

        double cur = 0;
        for (Opening o : openings) {
            double a = Math.max(o.offset(), cur), b = Math.min(o.endOffset(), len);
            if (b <= a) continue;
            if (a > cur) createWallBox(w, ux, uy, deg, cur, a, 0, h, t);
            double top = Math.min(h, o.sill() + o.height());
            if (o.sill() > 0) createWallBox(w, ux, uy, deg, a, b, 0, Math.min(o.sill(), h), t);
            if (top < h) createWallBox(w, ux, uy, deg, a, b, top, h, t); // 문 위의 상인방(Header)
            cur = b;
        }
        if (cur < len) createWallBox(w, ux, uy, deg, cur, len, 0, h, t);
    }

    private void createWallBox(Wall w, double ux, double uy, double deg,
                               double a, double b, double bottom, double top, double thickness) {
        double segLen = b - a;
        double segH = top - bottom;
        if (segLen <= 0 || segH <= 0) return;

        double mid = (a + b) / 2.0;
        double cx = w.start().x() + ux * mid;
        double cy = w.start().y() + uy * mid;

        Box box = new Box(segLen, segH, thickness);
        box.setMaterial(wallMat);
        box.getTransforms().add(new Rotate(-deg, Rotate.Y_AXIS));
        box.setTranslateX(cx);
        box.setTranslateY(-(bottom + top) / 2.0);
        box.setTranslateZ(-cy);
        world.getChildren().add(box);
    }

    private static PhongMaterial material(Color c) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSpecularColor(Color.gray(0.3));
        return m;
    }
}
