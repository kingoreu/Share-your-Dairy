package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

public class MainController {
    @FXML private Pane contentPane;

    @FXML private Rectangle wardrobeHotspot;
    @FXML private Rectangle windowHotspot;
    @FXML private Rectangle laptopHotspot;
    @FXML private Rectangle bookshelfHotspot;
    @FXML private Rectangle radioHotspot;

    @FXML private ImageView characterImg;

    @FXML
    public void initialize() {
        // 오버레이 패널 기본 상태: 보이지 않음 + 마우스 통과
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        contentPane.setPickOnBounds(true);
        contentPane.setMouseTransparent(true); // ★ 숨길 땐 클릭 통과
        contentPane.setStyle("-fx-background-color: transparent;");
        contentPane.toBack();

        // Z-Order (핫스팟/캐릭터는 앞으로)
        wardrobeHotspot.toFront();
        windowHotspot.toFront();
        laptopHotspot.toFront();
        bookshelfHotspot.toFront();
        radioHotspot.toFront();
        characterImg.toFront();
        setOverlayVisible(true);

        // ESC로 닫기
        contentPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) closeContent();
                });
            }
        });
    }

    // 클릭 이벤트 핸들러
    @FXML private void onWardrobeClicked(MouseEvent e)   { /* TODO */ }
    @FXML private void onWindowClicked(MouseEvent e)     { loadView("/fxml/moodGraph/mood-graph-view.fxml"); }
//    @FXML private void onLaptopClicked(MouseEvent e) {
//        Platform.runLater(() -> Router.go("DiaryHub"));
//    }
    // @FXML private void onLaptopClicked(MouseEvent e)     { loadView("/fxml/diary/my_diary/my-diary-view.fxml"); }
    @FXML private void onLaptopClicked(MouseEvent e)     { loadView("/fxml/diary/diary_hub/diary-hub-shell.fxml"); }
    @FXML private void onBookshelfClicked(MouseEvent e)  { /* TODO */ }
    @FXML private void onRadioClicked(MouseEvent e)      { loadView("/fxml/diary/our_diary/home-view.fxml"); }
    @FXML private void onCharacterClicked(MouseEvent e)  { loadView("/fxml/userInfo/settings-view.fxml"); }

    // 뷰 전환 로직
    private void loadView(String fxmlPath) {
        try {
            var url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxmlPath);

            Parent view = FXMLLoader.load(url);

            contentPane.getChildren().setAll(view);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            contentPane.setMouseTransparent(false); // ★ 보여줄 땐 입력 받기
            contentPane.toFront();

            // 전환 뷰가 컨테이너 채우도록 바인딩
            if (view instanceof javafx.scene.layout.Region r) {
                r.prefWidthProperty().bind(contentPane.widthProperty());
                r.prefHeightProperty().bind(contentPane.heightProperty());
            }

            // 메인 오버레이 숨김
            setOverlayVisible(false);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 오버레이 가시성 설정
    private void setOverlayVisible(boolean v) {
        wardrobeHotspot.setVisible(v);
        windowHotspot.setVisible(v);
        laptopHotspot.setVisible(v);
        bookshelfHotspot.setVisible(v);
        radioHotspot.setVisible(v);
        characterImg.setVisible(v);
        if (v) {
            // 오버레이가 보일 땐 항상 앞에 오도록
            wardrobeHotspot.toFront();
            windowHotspot.toFront();
            laptopHotspot.toFront();
            bookshelfHotspot.toFront();
            radioHotspot.toFront();
            characterImg.toFront();
        }
    }

    // 콘텐츠 닫기
    private void closeContent() {
        contentPane.getChildren().clear();
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        contentPane.setMouseTransparent(true); // ★ 다시 클릭 통과
        contentPane.toBack();
        setOverlayVisible(true);
    }
}
