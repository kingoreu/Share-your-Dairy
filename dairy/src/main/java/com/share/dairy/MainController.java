package com.share.dairy;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class MainController {
    @FXML private Pane contentPane;
    @FXML private ImageView wardrobeHotspot;
    @FXML private ImageView windowHotspot;
    @FXML private ImageView laptopHotspot;
    @FXML private ImageView bookshelfHotspot;
    @FXML private ImageView radioHotspot;
    @FXML private ImageView characterImg;

    @FXML
    public void initialize() {
        // z-order 보장
        wardrobeHotspot.toFront();
        windowHotspot.toFront();
        laptopHotspot.toFront();
        bookshelfHotspot.toFront();
        radioHotspot.toFront();
        characterImg.toFront();
        contentPane.setVisible(false);
    }

    @FXML private void onWardrobeClicked(MouseEvent e)   {/* */}
    @FXML private void onWindowClicked(MouseEvent e)     { loadView("mood-graph-view.fxml");}
    @FXML private void onLaptopClicked(MouseEvent e)     { loadView("my-diary-view.fxml"); }
    @FXML private void onBookshelfClicked(MouseEvent e)  { /* */ }
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
