package com.share.dairy.controller;

import com.share.dairy.app.Router;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.net.URL;

public class DiaryHubController {

    @FXML private StackPane content;       // 쉘의 중앙 StackPane (FAB 포함)
    @FXML private StackPane centerHolder;  // ★ 전환 대상 컨테이너

    // ESC → 홈
    private final EventHandler<KeyEvent> escFilter = e -> {
        if (e.getCode() == KeyCode.ESCAPE) {
            System.out.println("[ESC] go Home");
            Router.go("Home");
            e.consume();
        }
    };

    @FXML
    public void initialize() {
        // ESC 필터 등록/해제
        content.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            if (scene != null)     scene.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
        });

        // 앱 진입 시 허브 리스트를 센터에 표시
        showDiaryHub();
    }

    // 좌/상단 버튼 핸들러 — "센터만" 교체
    @FXML private void showDiaryHub()   { setCenter("/fxml/diary/diary_hub/hub-list.fxml"); }
    @FXML private void showMyDiary()    { setCenter("/fxml/diary/my_diary/my-diary-view.fxml"); }
    @FXML private void showOurDiary()   { setCenter("/fxml/diary/our_diary/our-diary-view.fxml"); }
    @FXML private void showBuddyDiary() { setCenter("/fxml/diary/buddy_diary/buddy-diary-view.fxml"); }
    
    /** 중앙 영역 교체 (FAB/Top/Left는 그대로) */
    private void setCenter(String fxml) {
        try {
            URL url = getClass().getResource(fxml);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxml);

            FXMLLoader loader = new FXMLLoader(url);
            Parent node = loader.load();

            centerHolder.getChildren().setAll(node);

            if (node instanceof Region r) {
                r.prefWidthProperty().bind(centerHolder.widthProperty());
                r.prefHeightProperty().bind(centerHolder.heightProperty());
            }
            content.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ▼ 옵션: 허브 전체 FXML을 통째로 띄우고 싶을 때
    @FXML private void showDiaryHubFull() { replaceRoot("/fxml/diary/diary_hub/diary-hub-shell.fxml"); }
    private void replaceRoot(String fxml) {
        try {
            var url = getClass().getResource(fxml);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxml);
            var root = new FXMLLoader(url).load();
            content.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
}
