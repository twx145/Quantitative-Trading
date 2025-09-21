package com.twx.platform.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color; // 保持导入，但不再使用 scene.setFill
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("main-view.fxml"));
        Parent root = fxmlLoader.load();

        // ★ 性能优化 1: 将样式从 TRANSPARENT 改回 UNDECORATED
        // 这会创建一个不透明的窗口，极大地提升渲染性能。
        stage.initStyle(StageStyle.UNDECORATED);

        // ★ 性能优化 2: 创建一个标准的 Scene
        // 由于窗口不再透明，我们不再需要设置 scene.setFill(Color.TRANSPARENT)
        Scene scene = new Scene(root, 1500, 900);

        UIController controller = fxmlLoader.getController();
        controller.setStage(stage);

        stage.setTitle("专业量化交易回测平台");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}