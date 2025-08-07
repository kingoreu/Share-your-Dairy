package com.dairy.dairy;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    @FXML
    private StackPane contentArea;

    @FXML
    private void showHome() throws IOException {
        setContent("home-view.fxml");
    }

    @FXML
    private void showMyDiary() throws IOException {
        setContent("my-diary-view.fxml");
    }

    @FXML
    private void showOurDiary() throws IOException {
        setContent("our-diary-view.fxml");
    }

    @FXML
    private void showBuddyDiary() throws IOException {
        setContent("buddy-diary-view.fxml");
    }

    @FXML
    private void showSettings() throws IOException {
        setContent("settings-view.fxml");
    }

    @FXML
    private void showMoodGraph() throws IOException {
        setContent("mood-graph-view.fxml");
    }
    private void setContent(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
        Pane newPane = loader.load();
        contentArea.getChildren().setAll(newPane);
    }
}
