package com.dairy.dairy;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class SettingsController {

    @FXML
    private Label titleLabel;

    @FXML
    public void initialize() {
        System.out.println("SettingsController 초기화됨");
    }
}