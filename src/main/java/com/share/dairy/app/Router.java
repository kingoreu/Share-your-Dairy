package com.share.dairy.app;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;

public class Router {
    private static Stage stage;
    private static Scene scene;
    private static final Deque<Parent> history = new ArrayDeque<>();

    private static final Map<String, String> ROUTES = Map.of(
            "Home",     "/fxml/mainFrame/Main.fxml", // ← 기존 네 코드 그대로

            "DiaryHub", "/fxml/diary/diary_hub/diary-hub-view.fxml"
    );

    public static void init(Stage s) {
        stage = s;
        // Scene은 한 번만 생성
        scene = new Scene(new javafx.scene.layout.StackPane(), 960, 640);
        // 스타일시트도 한 번만
        var css = Router.class.getResource("/css/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Share Diary");
        stage.setResizable(true);
        stage.show();
        // 시작 화면
        go("Home", false); // history에 안 쌓고 시작
    }

    public static void go(String name) { go(name, true); }

    private static void go(String name, boolean pushHistory) {
        Platform.runLater(() -> {
            try {
                String fxml = ROUTES.get(name);
                if (fxml == null) throw new IllegalArgumentException("Unknown view: " + name);

                URL url = Router.class.getResource(fxml);
                if (url == null) throw new IllegalStateException("FXML not found: " + fxml);

                FXMLLoader loader = new FXMLLoader(url);
                Parent root = loader.load();

                // 이전 root를 히스토리에 저장 (옵션)
                if (pushHistory && scene.getRoot() != null) {
                    history.push(scene.getRoot());
                }

                // 새 루트로 교체 (Scene 재사용)
                scene.setRoot(root);

                // Region이면 화면을 꽉 채우도록
                if (root instanceof Region r) {
                    r.prefWidthProperty().bind(scene.widthProperty());
                    r.prefHeightProperty().bind(scene.heightProperty());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static boolean canGoBack() { return !history.isEmpty(); }

    public static void back() {
        if (!canGoBack()) return;
        Parent prev = history.pop();
        scene.setRoot(prev);
    }
}