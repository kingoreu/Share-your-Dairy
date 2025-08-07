package com.share.dairy;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class OurDiaryController {

    @FXML
    private Label titleLabel;

    @FXML
    public void initialize() {
        System.out.println("OurDiaryController 초기화됨");
    }
}
