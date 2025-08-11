package com.share.dairy.controller;

import com.share.dairy.app.Router;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;                 // ✅ 추가
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;    // ✅ 추가
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;

public class DiaryHubController {
    @FXML private StackPane content;   // center의 StackPane (전환 영역)
    @FXML private VBox listBox;        // ScrollPane 안의 VBox (카드 리스트)

    // ESC 필터
    private final EventHandler<KeyEvent> escFilter = e -> {
        if (e.getCode() == KeyCode.ESCAPE) {
            System.out.println("[ESC] go Home");
            Router.go("Home");
            e.consume();
        }
    };

    @FXML
    public void initialize() {
        // ✅ ESC 필터 등록/해제 (자식이 consume 해도 동작)
        content.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            if (scene != null)     scene.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
        });

        // ✅ 허브 진입 시 더미 카드 표시 (FXML에 fx:id="listBox" 있어야 함)
        if (listBox != null) {
            listBox.getChildren().setAll(
                diaryCard("2025.08.01", "제목1", "내용1"),
                diaryCard("2025.07.29", "제목2", "내용2")
            );
        }
    }

    private Node diaryCard(String date, String title, String contents) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");
        card.getChildren().addAll(
            new Label("DATE " + date),
            new Label("TITLE"),
            new Separator(),
            new Label("CONTENTS")
        );
        // 리스트 폭에 맞춰 꽉 차게
        if (listBox != null) card.maxWidthProperty().bind(listBox.widthProperty().subtract(8));
        return card;
    }

    // 좌측 버튼 핸들러들 (전환 시 content 안을 새로운 뷰로 교체)
    @FXML private void showMyDiary()    { setCenter("/fxml/diary/my_diary/my-diary-view.fxml"); }
    @FXML private void showOurDiary()   { setCenter("/fxml/diary/our_diary/our-diary-view.fxml"); }
    @FXML private void showBuddyDiary() { setCenter("/fxml/diary/buddy_diary/buddy-diary-view.fxml"); }

    private void setCenter(String fxml) {
        try {
            URL url = getClass().getResource(fxml);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxml);

            FXMLLoader loader = new FXMLLoader(url);
            Parent node = loader.load();

            content.getChildren().setAll(node);

            if (node instanceof javafx.scene.layout.Region r) {
                r.prefWidthProperty().bind(content.widthProperty());
                r.prefHeightProperty().bind(content.heightProperty());
            }
            content.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
