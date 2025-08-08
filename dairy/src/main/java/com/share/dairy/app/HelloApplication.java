package com.share.dairy.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class HelloApplication extends Application {
 @Override
    public void start(Stage stage) throws Exception {
        // ClassLoader 로드 기준 → resources 폴더 바로 아래를 가리킵니다.
        URL fxmlUrl = getClass().getClassLoader()
            .getResource("fxml/diary/mainFrame/Main.fxml");
        if (fxmlUrl == null) {
            throw new RuntimeException("Cannot find FXML resource: fxml/diary/mainFrame/Main.fxml");
        }

        Parent root = FXMLLoader.load(fxmlUrl);
        Scene scene = new Scene(root, 800, 600);

        stage.setTitle("공유일기");
        stage.setScene(scene);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }
}
