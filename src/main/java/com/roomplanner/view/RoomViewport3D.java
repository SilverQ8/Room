package com.roomplanner.view;

import com.roomplanner.model.FurnitureItem;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

public class RoomViewport3D extends Pane {
    // 방 기본 치수 (단위: cm)
    private final double roomWidth = 400;
    private final double roomLength = 400;
    private final double roomHeight = 220;

    // 3D 루트 그룹
    private final Group root3D = new Group();
    private final Group world = new Group();
    private final Group furnitureGroup = new Group();
    private final SubScene subScene;

    // 카메라 및 궤도 변환
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Translate cameraPosition = new Translate(0, 0, -850);
    private final Rotate cameraRotateX = new Rotate(-35, Rotate.X_AXIS); // 위에서 내려다보는 각도
    private final Rotate cameraRotateY = new Rotate(45, Rotate.Y_AXIS);  // 45도 아이소메트릭 느낌
    private final Translate cameraPivot = new Translate(0, -60, 0);

    // 카메라 드래그 상태
    private double mouseAnchorX;
    private double mouseAnchorY;

    // 바닥 메쉬 (Ray picking용)
    private Box floorBox;

    // 가구 리스트 & 선택
    private final ObservableList<FurnitureItem> furnitures = FXCollections.observableArrayList();
    private final ObjectProperty<FurnitureItem> selectedItem = new SimpleObjectProperty<>(null);

    // 가구 드래그 상태
    private boolean isDraggingFurniture = false;
    private double dragStartFurnitureX;
    private double dragStartFurnitureZ;
    private Point3D dragStartFloorPoint;

    // 리스너 콜백
    private Runnable onSelectionChanged;

    public RoomViewport3D() {
        // 카메라 설정
        camera.setNearClip(1.0);
        camera.setFarClip(4000.0);
        camera.setFieldOfView(42);

        Group cameraGroup = new Group();
        cameraGroup.getTransforms().addAll(cameraPivot, cameraRotateY, cameraRotateX, cameraPosition);
        cameraGroup.getChildren().add(camera);

        world.getChildren().addAll(furnitureGroup);
        root3D.getChildren().addAll(world, cameraGroup);

        buildRoom();
        setupLighting();

        subScene = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFill(Color.web("#1e232a")); // 세련된 다크 테마 배경

        getChildren().add(subScene);

        // 창 크기에 맞춰 3D SubScene 리사이징
        widthProperty().addListener((obs, oldVal, newVal) -> subScene.setWidth(newVal.doubleValue()));
        heightProperty().addListener((obs, oldVal, newVal) -> subScene.setHeight(newVal.doubleValue()));

        setupMouseInteraction();
    }

    private void buildRoom() {
        // 1. 바닥
        floorBox = new Box(roomWidth, 2, roomLength);
        floorBox.setTranslateY(1); // 윗면이 Y=0에 오도록
        PhongMaterial floorMat = new PhongMaterial(Color.web("#e0d4c3")); // 원목 마루 느낌
        floorMat.setSpecularColor(Color.color(0.1, 0.1, 0.1));
        floorBox.setMaterial(floorMat);
        world.getChildren().add(floorBox);

        // 2. 바닥 그리드 라인 (50cm 간격)
        Group gridGroup = new Group();
        PhongMaterial gridMat = new PhongMaterial(Color.web("#c4b7a6"));
        double step = 50.0;
        for (double x = -roomWidth / 2.0; x <= roomWidth / 2.0; x += step) {
            Box line = new Box(1, 0.5, roomLength);
            line.setTranslateX(x);
            line.setTranslateY(-0.1);
            line.setMaterial(gridMat);
            gridGroup.getChildren().add(line);
        }
        for (double z = -roomLength / 2.0; z <= roomLength / 2.0; z += step) {
            Box line = new Box(roomWidth, 0.5, 1);
            line.setTranslateZ(z);
            line.setTranslateY(-0.1);
            line.setMaterial(gridMat);
            gridGroup.getChildren().add(line);
        }
        world.getChildren().add(gridGroup);

        // 3. 벽체 (시야를 확보하기 위해 뒤쪽 벽과 왼쪽 벽 2면 배치 - 코너 룸 스타일)
        PhongMaterial wallMat = new PhongMaterial(Color.web("#f4f5f7"));
        wallMat.setSpecularColor(Color.color(0.05, 0.05, 0.05));

        // 뒤쪽 벽 (Z = -roomLength / 2)
        Box backWall = new Box(roomWidth, roomHeight, 6);
        backWall.setTranslateY(-roomHeight / 2.0);
        backWall.setTranslateZ(-roomLength / 2.0 - 3);
        backWall.setMaterial(wallMat);

        // 왼쪽 벽 (X = -roomWidth / 2)
        Box leftWall = new Box(6, roomHeight, roomLength);
        leftWall.setTranslateX(-roomWidth / 2.0 - 3);
        leftWall.setTranslateY(-roomHeight / 2.0);
        leftWall.setMaterial(wallMat);

        // 걸레받이 (Baseboard) 디테일
        PhongMaterial baseboardMat = new PhongMaterial(Color.web("#8b7355"));
        Box bbBack = new Box(roomWidth, 10, 4);
        bbBack.setTranslateY(-5);
        bbBack.setTranslateZ(-roomLength / 2.0 + 2);
        bbBack.setMaterial(baseboardMat);

        Box bbLeft = new Box(4, 10, roomLength);
        bbLeft.setTranslateX(-roomWidth / 2.0 + 2);
        bbLeft.setTranslateY(-5);
        bbLeft.setMaterial(baseboardMat);

        world.getChildren().addAll(backWall, leftWall, bbBack, bbLeft);
    }

    private void setupLighting() {
        // 전체 부드러운 주변광
        AmbientLight ambient = new AmbientLight(Color.color(0.65, 0.65, 0.68));

        // 천장 중앙 포인트 조명
        PointLight ceilingLight = new PointLight(Color.color(0.9, 0.88, 0.82));
        ceilingLight.setTranslateX(0);
        ceilingLight.setTranslateY(-roomHeight + 30);
        ceilingLight.setTranslateZ(0);

        // 정면 보조광
        PointLight fillLight = new PointLight(Color.color(0.4, 0.4, 0.45));
        fillLight.setTranslateX(roomWidth * 0.8);
        fillLight.setTranslateY(-roomHeight * 0.8);
        fillLight.setTranslateZ(roomLength * 0.8);

        root3D.getChildren().addAll(ambient, ceilingLight, fillLight);
    }

    private void setupMouseInteraction() {
        // 마우스 클릭
        subScene.setOnMousePressed(e -> {
            mouseAnchorX = e.getSceneX();
            mouseAnchorY = e.getSceneY();

            if (e.getButton() == MouseButton.PRIMARY) {
                // 클릭된 노드 검사
                PickResult pr = e.getPickResult();
                Node hitNode = pr.getIntersectedNode();

                FurnitureItem clickedItem = findFurnitureByNode(hitNode);
                if (clickedItem != null) {
                    selectFurniture(clickedItem);
                    isDraggingFurniture = true;
                    dragStartFurnitureX = clickedItem.getX();
                    dragStartFurnitureZ = clickedItem.getZ();
                    dragStartFloorPoint = getFloorIntersection(pr);
                } else if (hitNode == floorBox) {
                    // 바닥 클릭 시 가구 선택 해제
                    selectFurniture(null);
                }
            }
        });

        // 마우스 드래그
        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - mouseAnchorX;
            double dy = e.getSceneY() - mouseAnchorY;

            // 가구 드래그 중인 경우
            if (isDraggingFurniture && selectedItem.get() != null && e.getButton() == MouseButton.PRIMARY) {
                PickResult pr = e.getPickResult();
                Point3D currentFloorPt = getFloorIntersection(pr);

                if (currentFloorPt != null && dragStartFloorPoint != null) {
                    double deltaX = currentFloorPt.getX() - dragStartFloorPoint.getX();
                    double deltaZ = currentFloorPt.getZ() - dragStartFloorPoint.getZ();

                    double targetX = dragStartFurnitureX + deltaX;
                    double targetZ = dragStartFurnitureZ + deltaZ;

                    // 방 경계 제한 (벽 밖으로 나가지 않도록)
                    double halfW = roomWidth / 2.0 - selectedItem.get().getWidth() / 2.0;
                    double halfL = roomLength / 2.0 - selectedItem.get().getDepth() / 2.0;
                    targetX = Math.max(-halfW, Math.min(halfW, targetX));
                    targetZ = Math.max(-halfL, Math.min(halfL, targetZ));

                    // 10cm 스냅 적용 (부드러운 정렬)
                    targetX = Math.round(targetX / 10.0) * 10.0;
                    targetZ = Math.round(targetZ / 10.0) * 10.0;

                    selectedItem.get().setX(targetX);
                    selectedItem.get().setZ(targetZ);

                    checkCollisions();
                    if (onSelectionChanged != null) onSelectionChanged.run();
                }
                return;
            }

            // 마우스 우클릭: 궤도 회전 (Orbit Camera)
            if (e.getButton() == MouseButton.SECONDARY) {
                cameraRotateY.setAngle(cameraRotateY.getAngle() + dx * 0.35);
                double newPitch = cameraRotateX.getAngle() - dy * 0.35;
                // 피치 각도 제한 (-85도 ~ -5도: 위에서 내려다보는 시점 유지)
                cameraRotateX.setAngle(Math.max(-85, Math.min(-5, newPitch)));
            }
            // 마우스 휠 클릭(MIDDLE): 시점 패닝 (Pan)
            else if (e.getButton() == MouseButton.MIDDLE) {
                cameraPivot.setX(cameraPivot.getX() - dx * 0.8);
                cameraPivot.setY(cameraPivot.getY() - dy * 0.8);
            }

            mouseAnchorX = e.getSceneX();
            mouseAnchorY = e.getSceneY();
        });

        // 드래그 종료
        subScene.setOnMouseReleased(e -> {
            isDraggingFurniture = false;
            dragStartFloorPoint = null;
        });

        // 마우스 휠: 줌 인 / 줌 아웃
        subScene.setOnScroll((ScrollEvent e) -> {
            double zoom = cameraPosition.getZ() + e.getDeltaY() * 1.5;
            // 줌 범위 제한 (-200cm ~ -2000cm)
            cameraPosition.setZ(Math.max(-2000, Math.min(-200, zoom)));
        });
    }

    private Point3D getFloorIntersection(PickResult pr) {
        if (pr == null) return null;
        if (pr.getIntersectedNode() == floorBox) {
            Point3D pt = pr.getIntersectedPoint();
            // 로컬 좌표계를 월드 좌표로 변환
            return floorBox.localToParent(pt);
        }
        // 다른 노드(가구 등)를 가리킨 경우 바닥 Y=0 평면상의 근사 계산
        Point3D pt = pr.getIntersectedPoint();
        if (pt != null && pr.getIntersectedNode() != null) {
            Point3D worldPt = pr.getIntersectedNode().localToScene(pt);
            return new Point3D(worldPt.getX(), 0, worldPt.getZ());
        }
        return null;
    }

    private FurnitureItem findFurnitureByNode(Node node) {
        if (node == null) return null;
        Node curr = node;
        while (curr != null && curr != furnitureGroup && curr != root3D) {
            for (FurnitureItem item : furnitures) {
                if (item.getNodeGroup() == curr) {
                    return item;
                }
            }
            curr = curr.getParent();
        }
        return null;
    }

    public void addFurniture(FurnitureItem.Type type) {
        // 방 중앙 근처에 생성
        double spawnX = (Math.random() - 0.5) * 60;
        double spawnZ = (Math.random() - 0.5) * 60;
        FurnitureItem item = new FurnitureItem(type, spawnX, spawnZ);

        furnitures.add(item);
        furnitureGroup.getChildren().add(item.getNodeGroup());

        selectFurniture(item);
        checkCollisions();
    }

    public void removeSelectedFurniture() {
        FurnitureItem item = selectedItem.get();
        if (item != null) {
            furnitures.remove(item);
            furnitureGroup.getChildren().remove(item.getNodeGroup());
            selectFurniture(null);
            checkCollisions();
        }
    }

    public void rotateSelected90() {
        FurnitureItem item = selectedItem.get();
        if (item != null) {
            item.rotate90();
            checkCollisions();
            if (onSelectionChanged != null) onSelectionChanged.run();
        }
    }

    public void selectFurniture(FurnitureItem item) {
        if (selectedItem.get() != null) {
            selectedItem.get().setSelected(false);
        }
        selectedItem.set(item);
        if (item != null) {
            item.setSelected(true);
        }
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    public void checkCollisions() {
        // 모든 가구의 충돌 플래그 리셋
        for (FurnitureItem item : furnitures) {
            item.setColliding(false);
        }

        // 쌍(Pair)별 3D 바운딩 박스 충돌 검사
        for (int i = 0; i < furnitures.size(); i++) {
            FurnitureItem a = furnitures.get(i);
            for (int j = i + 1; j < furnitures.size(); j++) {
                FurnitureItem b = furnitures.get(j);
                if (a.getEffectiveBounds().intersects(b.getEffectiveBounds())) {
                    a.setColliding(true);
                    b.setColliding(true);
                }
            }
        }
    }

    // 뷰 전환 프리셋
    public void resetView() {
        cameraPivot.setX(0);
        cameraPivot.setY(-60);
        cameraPivot.setZ(0);
        cameraRotateX.setAngle(-35);
        cameraRotateY.setAngle(45);
        cameraPosition.setZ(-850);
    }

    public void setTopDownView() {
        cameraPivot.setX(0);
        cameraPivot.setY(0);
        cameraPivot.setZ(0);
        cameraRotateX.setAngle(-89.9);
        cameraRotateY.setAngle(0);
        cameraPosition.setZ(-900);
    }

    // Getters and Listeners
    public ObservableList<FurnitureItem> getFurnitures() { return furnitures; }
    public FurnitureItem getSelectedItem() { return selectedItem.get(); }
    public ObjectProperty<FurnitureItem> selectedItemProperty() { return selectedItem; }
    public void setOnSelectionChanged(Runnable listener) { this.onSelectionChanged = listener; }
}

