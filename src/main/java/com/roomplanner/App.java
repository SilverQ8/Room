package com.roomplanner;

import com.roomplanner.model.FurnitureItem;
import com.roomplanner.view.PropertyPanel;
import com.roomplanner.view.RoomViewport3D;
import com.roomplanner.view.SidebarCatalog;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        RoomViewport3D viewport = new RoomViewport3D();
        SidebarCatalog catalog = new SidebarCatalog(viewport::addFurniture);
        PropertyPanel propertyPanel = new PropertyPanel(viewport);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e232a;");

        // 상단 툴바
        HBox toolbar = createToolbar(viewport);
        root.setTop(toolbar);

        // 중앙 3D 뷰포트
        root.setCenter(viewport);

        // 좌측 가구 카탈로그
        root.setLeft(catalog);

        // 우측 속성 패널
        root.setRight(propertyPanel);

        // 하단 상태바
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1280, 800);

        // 키보드 단축키 처리
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.R) {
                viewport.rotateSelected90();
            } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                viewport.removeSelectedFurniture();
            }
        });

        // 시작 시 기본 방 인테리어 프리셋 샘플 배치 (침대 1개, 책상 1개, 의자 1개)
        viewport.addFurniture(FurnitureItem.Type.BED);
        if (viewport.getSelectedItem() != null) {
            viewport.getSelectedItem().setX(-80);
            viewport.getSelectedItem().setZ(-80);
        }

        viewport.addFurniture(FurnitureItem.Type.DESK);
        if (viewport.getSelectedItem() != null) {
            viewport.getSelectedItem().setX(90);
            viewport.getSelectedItem().setZ(-120);
        }

        viewport.addFurniture(FurnitureItem.Type.CHAIR);
        if (viewport.getSelectedItem() != null) {
            viewport.getSelectedItem().setX(90);
            viewport.getSelectedItem().setZ(-60);
        }

        viewport.selectFurniture(null);
        viewport.checkCollisions();

        primaryStage.setTitle("🏠 3D 가구 배치 시뮬레이터 (JavaFX 3D Room Planner)");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createToolbar(RoomViewport3D viewport) {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #262c36; -fx-border-color: #384252; -fx-border-width: 0 0 1 0;");

        Label appTitle = new Label("🏠 3D Room Planner");
        appTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #58a6ff;");

        Button btnResetCam = createToolButton("📷 3D 뷰 리셋", () -> viewport.resetView());
        Button btnTopDown = createToolButton("📐 탑뷰 (2D 평면도)", () -> viewport.setTopDownView());
        Button btnRotate = createToolButton("🔄 선택 회전 (R)", () -> viewport.rotateSelected90());
        Button btnDelete = createToolButton("🗑️ 선택 삭제 (Del)", () -> viewport.removeSelectedFurniture());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                appTitle,
                new Separator(),
                btnResetCam,
                btnTopDown,
                new Separator(),
                btnRotate,
                btnDelete,
                spacer
        );

        return bar;
    }

    private Button createToolButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: #30363d; " +
                "-fx-text-fill: #e6edf3; " +
                "-fx-font-size: 12px; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #484f58; " +
                "-fx-border-radius: 6;"
        );
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(6, 15, 6, 15));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #1a1e24; -fx-border-color: #384252; -fx-border-width: 1 0 0 0;");

        Label tipLabel = new Label("💡 조작 팁: [마우스 우클릭 드래그] 3D 회전 | [마우스 휠] 줌 | [휠 클릭 드래그] 패닝 | [좌클릭 드래그] 가구 이동 | [R] 90도 회전 | [Del] 삭제");
        tipLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b949e;");

        bar.getChildren().add(tipLabel);
        return bar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

