package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

public class HubListController {

    @FXML private VBox listBox;

    @FXML
    public void initialize() {
        // 데모 카드 (서비스/DB 작업으로 대체 예정)
        listBox.getChildren().setAll(
            diaryCard("2025.08.01", "제목1", "내용1"),
            diaryCard("2025.07.29", "제목2", "내용2")
        );
    }

    private Node diaryCard(String date, String title, String contents) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");
        card.getChildren().addAll(
            new Label("DATE " + date),
            new Label("TITLE"),
            new Separator(),
            new Label("CONTENTS")
        );
        card.maxWidthProperty().bind(listBox.widthProperty().subtract(8));
        return card;
    }
}
