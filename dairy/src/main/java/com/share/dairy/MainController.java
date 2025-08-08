package com.share.dairy;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;  // 추가

public class MainController {
    @FXML private Pane contentPane;

    // ImageView → Rectangle 으로 타입 변경
    @FXML private Rectangle wardrobeHotspot;
    @FXML private Rectangle windowHotspot;
    @FXML private Rectangle laptopHotspot;
    @FXML private Rectangle bookshelfHotspot;
    @FXML private Rectangle radioHotspot;

    // 캐릭터는 여전히 ImageView
    @FXML private ImageView characterImg;

    @FXML
    public void initialize() {
        // z-order 보장: Shape 도 Front 로 올려줍니다
        wardrobeHotspot.toFront();
        windowHotspot.toFront();
        laptopHotspot.toFront();
        bookshelfHotspot.toFront();
        radioHotspot.toFront();
        characterImg.toFront();

        contentPane.setVisible(false);
    }

    @FXML private void onWardrobeClicked(MouseEvent e)   { /* TODO */ }
    @FXML private void onWindowClicked(MouseEvent e)     { loadView("mood-graph-view.fxml"); }
    @FXML private void onLaptopClicked(MouseEvent e)     { loadView("my-diary-view.fxml"); }
    @FXML private void onBookshelfClicked(MouseEvent e)  { /* TODO */ }
    @FXML private void onRadioClicked(MouseEvent e)      { loadView("home-view.fxml"); }
    @FXML private void onCharacterClicked(MouseEvent e)  { loadView("settings-view.fxml"); }

    private void loadView(String fxmlName) {
        try {
            Pane view = FXMLLoader.load(getClass().getResource(fxmlName));
            contentPane.getChildren().setAll(view);
            contentPane.setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
