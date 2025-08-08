package com.share.dairy;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Main.fxml 절대경로로 로드
        FXMLLoader fxmlLoader = new FXMLLoader(
            getClass().getResource("/com/share/dairy/Main.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
        stage.setTitle("공유일기");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
