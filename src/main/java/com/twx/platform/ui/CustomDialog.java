// 请用这份代码完整替换您现有的 CustomDialog.java

package com.twx.platform.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.Optional;

public class CustomDialog {

    public enum DialogType {
        INFORMATION,
        WARNING,
        ERROR
    }

    private static double xOffset = 0;
    private static double yOffset = 0;

    public static void show(Stage owner, DialogType type, String title, String content, boolean isDarkMode) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-pane");

        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("dialog-icon");

        switch (type) {
            case INFORMATION:
                icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 5h2v2h-2V7zm0 4h2v6h-2v-6z");
                icon.getStyleClass().add("dialog-icon-info");
                break;
            case WARNING:
                icon.setContent("M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2V7h2v7z");
                icon.getStyleClass().add("dialog-icon-warning");
                break;
            case ERROR:
                icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.93 12.5L12 9.57 7.07 14.5 5.66 13.09 10.59 8.16 5.66 3.23 7.07 1.82 12 6.75l4.93-4.93 1.41 1.41L13.41 8.16l4.93 4.93-1.41 1.41z");
                icon.getStyleClass().add("dialog-icon-error");
                break;
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        HBox titleBar = new HBox(15, icon, titleLabel);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("dialog-header");
        root.setTop(titleBar);

        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().add("dialog-content");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        VBox contentBox = new VBox(contentLabel);
        contentBox.getStyleClass().add("dialog-body");
        root.setCenter(contentBox);

        Button closeButton = new Button("确定");
        closeButton.getStyleClass().addAll("dialog-button", "dialog-button-primary");
        closeButton.setOnAction(e -> dialogStage.close());
        closeButton.setDefaultButton(true);
        HBox buttonBar = new HBox(closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getStyleClass().add("dialog-footer");
        root.setBottom(buttonBar);

        setupSceneAndStage(owner, dialogStage, root, isDarkMode);
        dialogStage.showAndWait();
    }

    public static Optional<String> showTextInput(Stage owner, String title, String header, String initialValue, boolean isDarkMode) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-pane");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        HBox titleBar = new HBox(titleLabel);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("dialog-header");
        root.setTop(titleBar);

        Label headerLabel = new Label(header);
        headerLabel.getStyleClass().add("dialog-content");
        TextField inputField = new TextField(initialValue);
        inputField.getStyleClass().add("dialog-text-input");
        VBox contentBox = new VBox(15, headerLabel, inputField);
        contentBox.getStyleClass().add("dialog-body");
        root.setCenter(contentBox);

        Button okButton = new Button("确定");
        okButton.getStyleClass().addAll("dialog-button", "dialog-button-primary");
        okButton.setDefaultButton(true);
        Button cancelButton = new Button("取消");
        cancelButton.getStyleClass().addAll("dialog-button", "dialog-button-secondary");
        cancelButton.setCancelButton(true);
        HBox buttonBar = new HBox(10, cancelButton, okButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.getStyleClass().add("dialog-footer");
        root.setBottom(buttonBar);

        final String[] result = {null};
        okButton.setOnAction(e -> {
            result[0] = inputField.getText();
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> dialogStage.close());

        setupSceneAndStage(owner, dialogStage, root, isDarkMode);
        dialogStage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private static void setupSceneAndStage(Stage owner, Stage dialogStage, BorderPane root, boolean isDarkMode) {
        root.setEffect(new DropShadow(20, Color.rgb(0, 0, 0, 0.15)));
        if (isDarkMode) {
            root.getStyleClass().add("theme-dark");
        }

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // 【健壮性优化】确保主窗口及其场景存在，才继承样式表
        if (owner != null && owner.getScene() != null && owner.getScene().getStylesheets() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        scene.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        scene.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset);
            dialogStage.setY(event.getScreenY() - yOffset);
        });

        dialogStage.setScene(scene);
    }
}
