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
        // 초기 상태
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        contentPane.setPickOnBounds(true);               // 뒤 클릭 차단
        contentPane.setStyle("-fx-background-color: white;");

        // Z-Order
        wardrobeHotspot.toFront();
        windowHotspot.toFront();
        laptopHotspot.toFront();
        bookshelfHotspot.toFront();
        radioHotspot.toFront();
        characterImg.toFront();
        setOverlayVisible(true);

        // ESC로 닫기: scene 생긴 뒤 '한 번'만 등록
        contentPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) closeContent();
                });
            }
        });
    }

    @FXML private void onWardrobeClicked(MouseEvent e)   { /* TODO */ }
    @FXML private void onWindowClicked(MouseEvent e)     { loadView("/fxml/moodGraph/mood-graph-view.fxml"); }
    @FXML private void onLaptopClicked(MouseEvent e)     { loadView("/fxml/my_diary/my-diary-view.fxml"); }
    @FXML private void onBookshelfClicked(MouseEvent e)  { /* TODO */ }
    @FXML private void onRadioClicked(MouseEvent e)      { loadView("/fxml/our_diary/home-view.fxml"); }
    @FXML private void onCharacterClicked(MouseEvent e)  { loadView("/fxml/userInfo/settings-view.fxml"); }

    private void loadView(String fxmlPath) {
        try {
            var url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxmlPath);

            Parent view = FXMLLoader.load(url);

            contentPane.getChildren().setAll(view);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            contentPane.toFront();

            // 전환 뷰가 컨테이너 꽉 채우도록
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

    private void setOverlayVisible(boolean v) {
        wardrobeHotspot.setVisible(v);
        windowHotspot.setVisible(v);
        laptopHotspot.setVisible(v);
        bookshelfHotspot.setVisible(v);
        radioHotspot.setVisible(v);
        characterImg.setVisible(v);
    }

    private void closeContent() {
        contentPane.getChildren().clear();
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        setOverlayVisible(true);
    }
}
