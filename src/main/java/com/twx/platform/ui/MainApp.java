package com.twx.platform.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image; // ★ 1. 导入Image类
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("main-view.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 1500, 900);

        UIController controller = fxmlLoader.getController();
        controller.setStage(stage);

        try {
            // 使用 getResourceAsStream 可以保证在打包成JAR文件后也能正确加载资源
            // 路径 "transparent.png" 是相对于 MainApp.class 文件的相对路径
            Image icon = new Image(Objects.requireNonNull(MainApp.class.getResourceAsStream("transparent.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("加载透明图标失败，请检查 'transparent.png' 文件是否存在于正确的资源路径下！");
            e.printStackTrace();
        }

        stage.setTitle("专业量化交易回测平台");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}