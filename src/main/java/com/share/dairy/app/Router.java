package com.share.dairy.app;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Router {
    private static Stage stage;

    public static void init(Stage s) {
        stage = s;
    }

    public static void go(String name) {
        try {
            String fxml;
            switch (name) {
                case "Home":
                    // ✅ 방 화면으로 돌아오기
                    fxml = "/fxml/mainFrame/Main.fxml";
                    break;
                case "DiaryHub":
                    // ✅ 실제 위치는 diary/diary_hub/diary-hub-view.fxml
                    fxml = "/fxml/diary/diary_hub/diary-hub-view.fxml";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown view: " + name);
            }

            Parent root = FXMLLoader.load(Router.class.getResource(fxml));
            Scene scene = new Scene(root, 960, 640);
            scene.getStylesheets().add(
                Router.class.getResource("/css/style.css").toExternalForm()
            );
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
