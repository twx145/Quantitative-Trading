package com.twx.platform.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow; // 【新增】导入阴影效果类
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class CustomDialog {

    public enum DialogType {
        INFORMATION,
        WARNING,
        ERROR
    }

    // 【新增】用于实现窗口拖动的成员变量
    private static double xOffset = 0;
    private static double yOffset = 0;

    public static void show(Stage owner, DialogType type, String title, String content, boolean isDarkMode) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        // --- 图标 ---
        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("dialog-icon");

        // 【修改】移除内联样式，改为添加CSS类名
        switch (type) {
            case INFORMATION:
                icon.setContent("M8,0 C3.582,0 0,3.582 0,8 C0,12.418 3.582,16 8,16 C12.418,16 16,12.418 16,8 C16,3.582 12.418,0 8,0 Z M7,4 H9 V6 H7 V4 Z M7,7 H9 V12 H7 V7 Z");
                icon.getStyleClass().add("dialog-icon-info");
                break;
            case WARNING:
                icon.setContent("M15.72,12.9l-6-10.38c-0.38-0.65-1.06-1.03-1.78-1.03c-0.72,0-1.4,0.38-1.78,1.03l-6,10.38 C-0.19,13.56,0.08,14.4,0.68,15c0.6,0.6,1.38,0.95,2.22,0.95h12c0.84,0,1.62-0.35,2.22-0.95 C17.92,14.4,18.19,13.56,15.72,12.9z M8,13c-0.55,0-1-0.45-1-1s0.45-1,1-1s1,0.45,1,1S8.55,13,8,13z M9,9H7V6h2V9z");
                icon.getStyleClass().add("dialog-icon-warning");
                break;
            case ERROR:
                icon.setContent("M8,0 C3.582,0 0,3.582 0,8 C0,12.418 3.582,16 8,16 C12.418,16 16,12.418 16,8 C16,3.582 12.418,0 8,0 Z M11.05,4.95 L8,8 L4.95,4.95 L4,5.9 L7.05,8.95 L4,12 L5,13 L8,10 L11,13 L12,12 L9,9 L12,6 L11.05,4.95 Z");
                icon.getStyleClass().add("dialog-icon-error");
                break;
        }

        // --- 文本内容 ---
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");

        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().add("dialog-content");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(350);

        VBox textVBox = new VBox(10, titleLabel, contentLabel);
        HBox contentHBox = new HBox(20, icon, textVBox);
        contentHBox.setPadding(new Insets(25, 25, 25, 25));
        contentHBox.setAlignment(Pos.CENTER_LEFT);

        // --- 按钮 ---
        Button closeButton = new Button("确定");
        closeButton.getStyleClass().add("dialog-button");
        closeButton.setOnAction(e -> dialogStage.close());
        closeButton.setDefaultButton(true); // 【新增】让用户按回车键即可关闭

        HBox buttonBar = new HBox(closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(0, 20, 20, 20));

        // --- 根布局 ---
        BorderPane root = new BorderPane();
        root.setCenter(contentHBox);
        root.setBottom(buttonBar);
        root.getStyleClass().add("dialog-pane");

        // 【新增】添加阴影效果
        DropShadow shadow = new DropShadow(15, Color.rgb(0, 0, 0, 0.2));
        root.setEffect(shadow);

        // --- 场景与舞台 ---
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // 【修改】从父窗口继承样式表，更健壮
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());

        if (isDarkMode) {
            root.getStyleClass().add("theme-dark");
        }

        // 【新增】实现窗口拖动
        scene.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        scene.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset);
            dialogStage.setY(event.getScreenY() - yOffset);
        });

        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }
}