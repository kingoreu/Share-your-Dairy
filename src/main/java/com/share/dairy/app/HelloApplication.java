package com.share.dairy.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class HelloApplication extends Application {
    
@Override
public void start(Stage stage) throws Exception {
    // 클래스패스 루트 기준으로 절대경로 사용
    Parent root = FXMLLoader.load(
      getClass().getResource("/fxml/mainFrame/Main.fxml")
    );
    Scene scene = new Scene(root, 800, 600);
    stage.setTitle("공유일기");
    stage.setScene(scene);
    stage.show();
}

    public static void main(String[] args) {
        launch();
    }
}
