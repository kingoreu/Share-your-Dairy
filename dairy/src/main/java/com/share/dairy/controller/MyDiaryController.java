package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MyDiaryController {

    @FXML
    private Label titleLabel;

    @FXML
    public void initialize() {
        System.out.println("MyDiaryController 초기화됨");
    }
}