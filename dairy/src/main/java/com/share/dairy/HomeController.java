package com.share.dairy;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class HomeController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button writeDiaryButton;

    @FXML
    public void initialize() {
        System.out.println("HomeController 초기화됨");
    }

    @FXML
    private void onWriteDiary() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("다이어리 작성");
        alert.setHeaderText(null);
        alert.setContentText("다이어리 작성 화면으로 이동합니다.");
        alert.showAndWait();
    }
}
