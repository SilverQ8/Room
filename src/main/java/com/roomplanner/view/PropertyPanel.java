package com.roomplanner.view;

import com.roomplanner.model.FurnitureItem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class PropertyPanel extends VBox {
    private final RoomViewport3D viewport;

    private final Label titleLabel = new Label("⚙️ 가구 속성 편집");
    private final Label noSelectionLabel = new Label("3D 공간에서 가구를 클릭하면\n치수, 각도, 색상을 조절할 수 있습니다.");

    private final VBox contentBox = new VBox(12);

    private final TextField nameField = new TextField();
    private final Slider sliderX = new Slider(-180, 180, 0);
    private final Slider sliderZ = new Slider(-180, 180, 0);
    private final Slider sliderRotation = new Slider(0, 360, 0);
    private final Slider sliderWidth = new Slider(30, 300, 100);
    private final Slider sliderDepth = new Slider(30, 300, 100);
    private final Slider sliderHeight = new Slider(20, 250, 100);
    private final ColorPicker colorPicker = new ColorPicker();

    private final Label lblX = new Label("0cm");
    private final Label lblZ = new Label("0cm");
    private final Label lblRot = new Label("0°");
    private final Label lblW = new Label("100cm");
    private final Label lblD = new Label("100cm");
    private final Label lblH = new Label("100cm");

    private boolean updatingFromModel = false;

    public PropertyPanel(RoomViewport3D viewport) {
        this.viewport = viewport;

        setPrefWidth(260);
        setSpacing(15);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #262c36; -fx-border-color: #384252; -fx-border-width: 0 0 0 1;");

        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #e6edf3;");
        noSelectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8b949e;");

        setupControls();

        getChildren().addAll(titleLabel, noSelectionLabel, contentBox);
        contentBox.setVisible(false);
        contentBox.setManaged(false);

        viewport.setOnSelectionChanged(this::updateFromSelection);
    }

    private void setupControls() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);

        int row = 0;
        grid.add(createHeaderLabel("이름"), 0, row);
        grid.add(nameField, 1, row++);

        grid.add(createHeaderLabel("X 위치"), 0, row);
        grid.add(new HBox(5, sliderX, lblX), 1, row++);

        grid.add(createHeaderLabel("Z 위치"), 0, row);
        grid.add(new HBox(5, sliderZ, lblZ), 1, row++);

        grid.add(createHeaderLabel("Y 회전"), 0, row);
        grid.add(new HBox(5, sliderRotation, lblRot), 1, row++);

        grid.add(createHeaderLabel("가로 (W)"), 0, row);
        grid.add(new HBox(5, sliderWidth, lblW), 1, row++);

        grid.add(createHeaderLabel("세로 (D)"), 0, row);
        grid.add(new HBox(5, sliderDepth, lblD), 1, row++);

        grid.add(createHeaderLabel("높이 (H)"), 0, row);
        grid.add(new HBox(5, sliderHeight, lblH), 1, row++);

        grid.add(createHeaderLabel("색상"), 0, row);
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        grid.add(colorPicker, 1, row++);

        Button btnRotate90 = new Button("🔄 90° 회전");
        btnRotate90.setMaxWidth(Double.MAX_VALUE);
        btnRotate90.setStyle("-fx-background-color: #30363d; -fx-text-fill: #58a6ff; -fx-cursor: hand;");
        btnRotate90.setOnAction(e -> viewport.rotateSelected90());

        Button btnDelete = new Button("🗑️ 가구 삭제");
        btnDelete.setMaxWidth(Double.MAX_VALUE);
        btnDelete.setStyle("-fx-background-color: #da3633; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnDelete.setOnAction(e -> viewport.removeSelectedFurniture());

        contentBox.getChildren().addAll(grid, btnRotate90, btnDelete);

        // 이벤트 바인딩
        nameField.textProperty().addListener((obs, oldV, newV) -> {
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setName(newV);
            }
        });

        sliderX.valueProperty().addListener((obs, oldV, newV) -> {
            lblX.setText(String.format("%.0fcm", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setX(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        sliderZ.valueProperty().addListener((obs, oldV, newV) -> {
            lblZ.setText(String.format("%.0fcm", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setZ(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        sliderRotation.valueProperty().addListener((obs, oldV, newV) -> {
            lblRot.setText(String.format("%.0f°", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setRotationAngle(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        sliderWidth.valueProperty().addListener((obs, oldV, newV) -> {
            lblW.setText(String.format("%.0fcm", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setWidth(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        sliderDepth.valueProperty().addListener((obs, oldV, newV) -> {
            lblD.setText(String.format("%.0fcm", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setDepth(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        sliderHeight.valueProperty().addListener((obs, oldV, newV) -> {
            lblH.setText(String.format("%.0fcm", newV.doubleValue()));
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setHeight(newV.doubleValue());
                viewport.checkCollisions();
            }
        });

        colorPicker.setOnAction(e -> {
            if (!updatingFromModel && viewport.getSelectedItem() != null) {
                viewport.getSelectedItem().setColor(colorPicker.getValue());
            }
        });
    }

    private Label createHeaderLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 11px;");
        return lbl;
    }

    private void updateFromSelection() {
        FurnitureItem item = viewport.getSelectedItem();
        if (item == null) {
            noSelectionLabel.setVisible(true);
            noSelectionLabel.setManaged(true);
            contentBox.setVisible(false);
            contentBox.setManaged(false);
            return;
        }

        noSelectionLabel.setVisible(false);
        noSelectionLabel.setManaged(false);
        contentBox.setVisible(true);
        contentBox.setManaged(true);

        updatingFromModel = true;
        nameField.setText(item.getName());
        sliderX.setValue(item.getX());
        sliderZ.setValue(item.getZ());
        sliderRotation.setValue(item.getRotationAngle());
        sliderWidth.setValue(item.getWidth());
        sliderDepth.setValue(item.getDepth());
        sliderHeight.setValue(item.getHeight());
        colorPicker.setValue(item.getColor());

        lblX.setText(String.format("%.0fcm", item.getX()));
        lblZ.setText(String.format("%.0fcm", item.getZ()));
        lblRot.setText(String.format("%.0f°", item.getRotationAngle()));
        lblW.setText(String.format("%.0fcm", item.getWidth()));
        lblD.setText(String.format("%.0fcm", item.getDepth()));
        lblH.setText(String.format("%.0fcm", item.getHeight()));
        updatingFromModel = false;
    }
}

