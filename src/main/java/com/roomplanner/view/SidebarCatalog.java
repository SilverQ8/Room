package com.roomplanner.view;

import com.roomplanner.model.FurnitureItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SidebarCatalog extends VBox {
    public SidebarCatalog(Consumer<FurnitureItem.Type> onAddFurniture) {
        setPrefWidth(220);
        setSpacing(12);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #262c36; -fx-border-color: #384252; -fx-border-width: 0 1 0 0;");

        Label titleLabel = new Label("📦 가구 카탈로그");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #e6edf3;");

        Label descLabel = new Label("클릭하여 방에 가구를 추가하세요.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b949e;");

        VBox itemsBox = new VBox(10);
        itemsBox.setPadding(new Insets(5, 0, 5, 0));

        for (FurnitureItem.Type type : FurnitureItem.Type.values()) {
            Button itemBtn = createCatalogButton(type, onAddFurniture);
            itemsBox.getChildren().add(itemBtn);
        }

        ScrollPane scrollPane = new ScrollPane(itemsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, descLabel, scrollPane);
    }

    private Button createCatalogButton(FurnitureItem.Type type, Consumer<FurnitureItem.Type> onAddFurniture) {
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(10, 12, 10, 12));

        String icon = switch (type) {
            case BED -> "🛏️";
            case DESK -> "🖥️";
            case CHAIR -> "🪑";
            case WARDROBE -> "🚪";
            case SOFA -> "🛋️";
            case TV_STAND -> "📺";
        };

        btn.setText(icon + " " + type.label + "\n" + (int) type.defaultWidth + " x " + (int) type.defaultDepth + " x " + (int) type.defaultHeight + "cm");
        btn.setStyle(
                "-fx-background-color: #30363d; " +
                "-fx-text-fill: #e6edf3; " +
                "-fx-font-size: 12px; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #484f58; " +
                "-fx-border-radius: 6;"
        );

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #3b434e; -fx-text-fill: #58a6ff; -fx-background-radius: 6; -fx-border-color: #58a6ff; -fx-border-radius: 6; -fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: #30363d; -fx-text-fill: #e6edf3; -fx-background-radius: 6; -fx-border-color: #484f58; -fx-border-radius: 6;"
        ));

        btn.setOnAction(e -> onAddFurniture.accept(type));
        return btn;
    }
}

