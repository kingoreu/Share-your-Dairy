package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class MyDiaryController {
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    @FXML public void initialize() {

        titleField.setDisable(false);
        contentArea.setDisable(false);
    }

    @FXML private void onPlace(){ placeField.requestFocus(); }
    @FXML private void onMusic(){ musicField.requestFocus(); }
    @FXML private void onTime(){  timeField.requestFocus();  }

    @FXML private void onEdit(){
        titleField.setDisable(false);
        contentArea.setDisable(false);
    }

    @FXML private void onSave(){
        // TODO: DB/파일 저장 연동
        new Alert(Alert.AlertType.INFORMATION, "저장 완료!").showAndWait();
        titleField.setDisable(true);
        contentArea.setDisable(true);
    }
}