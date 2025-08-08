package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class BuddyDiaryController {

    @FXML
    private Label titleLabel;

    @FXML
    public void initialize() {
        System.out.println("BuddyDiaryController 초기화됨");
    }
}