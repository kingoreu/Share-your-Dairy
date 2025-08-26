package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.DialogPane;

public class NewShareDiaryDialogController {
    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private DialogPane dialogPane;

    @FXML
    public void initialize() {
        // OK 클릭 시 유효성 체크 등 필요하면 여기서
        // dialogPane.lookupButton(ButtonType.OK) 로 버튼 접근 가능
    }

    public String getTitleText() { return titleField.getText(); }
    public String getContentText() { return contentArea.getText(); }
}