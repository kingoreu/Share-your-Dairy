package com.share.dairy.app.music;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class MiniPlayer {
    private static MiniPlayer INSTANCE;

    private final Stage stage;
    private final WebView web;
    private final Label title;

    private MiniPlayer() {
        stage = new Stage();
        stage.setTitle("Mini Player");
        stage.setAlwaysOnTop(true); // 항상 위(원하면 제거)
        web = new WebView();
        title = new Label("");

        Button hide = new Button("숨기기");
        hide.setOnAction(e -> stage.hide()); // 숨겨도 재생은 계속됨 (창만 가림)

        VBox root = new VBox(6, title, web, hide);
        VBox.setVgrow(web, Priority.ALWAYS);
        stage.setScene(new Scene(root, 420, 320));
    }

    public static MiniPlayer get() {
        if (INSTANCE == null) INSTANCE = new MiniPlayer();
        return INSTANCE;
    }

    /** 선택한 영상 재생(autoplay) */
    public void play(String videoId, String titleText) {
        Platform.runLater(() -> {
            title.setText(titleText == null ? "" : titleText);
            web.getEngine().load("https://www.youtube.com/watch?v=" + videoId + "&autoplay=1");
            if (!stage.isShowing()) stage.show();
            stage.toFront();
        });
    }
}
