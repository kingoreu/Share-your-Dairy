package com.share.dairy;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class HelloApplication extends Application {
    @Override
public void start(Stage stage) throws IOException {
    FXMLLoader fxmlLoader = new FXMLLoader(
        getClass().getResource("/com/share/dairy/Main.fxml")
    );
    // Main.fxml 에 맞춘 800×600 으로 씬 크기 설정
    Scene scene = new Scene(fxmlLoader.load(), 800, 600);
    stage.setTitle("공유일기");
    stage.setScene(scene);
    stage.show();
}
    public static void main(String[] args) {
        launch();
    }
}
