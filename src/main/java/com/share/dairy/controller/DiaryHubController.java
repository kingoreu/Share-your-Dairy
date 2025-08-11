package com.share.dairy.controller;

import com.share.dairy.app.Router;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import java.net.URL;

public class DiaryHubController {
    @FXML private StackPane content;

    @FXML
    public void initialize() {
        // ESC: 허브에서 방 화면으로 복귀
        content.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        Router.go("Home");
                        e.consume();
                    }
                });
            }
        });
        content.setFocusTraversable(true);
        content.requestFocus();

        // ✅ 초기에는 아무 것도 띄우지 않음(= 5-1 허브 기본 화면)
        content.getChildren().clear();
    }

    // 왼쪽 버튼 핸들러들
    @FXML
    private void showMyDiary() {
        // ✅ MY DIARY 버튼 클릭 시 → 작성 화면(5-2)
        setCenter("/fxml/diary/my_diary/my-diary-view.fxml");
    }

    @FXML
    private void showOurDiary() {
        // OUR DIARY(5-3)
        setCenter("/fxml/diary/our_diary/our-diary-view.fxml");
    }

    @FXML
    private void showBuddyDiary() {
        // BUDDY DIARY(5-5)
        setCenter("/fxml/diary/buddy_diary/buddy-diary-view.fxml");
    }

    private void setCenter(String fxml) {
        try {
            URL url = getClass().getResource(fxml);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxml);
            Node node = FXMLLoader.load(url);
            content.getChildren().setAll(node);
            content.requestFocus(); // ESC 포커스 유지
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
