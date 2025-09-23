package com.twx.platform.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
// ★ 1. 导入Image类
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("main-view.fxml"));
        Parent root = fxmlLoader.load();

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