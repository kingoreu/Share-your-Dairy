package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.service.diary.DiaryWriteService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class HubListController {

    @FXML private VBox listContainer;

    private final DiaryWriteService service = new DiaryWriteService();
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML
    private void initialize() {
        refreshList();
    }

    /** DB → 카드 렌더 */
    public void refreshList() {
        List<DiaryEntry> rows;
        try {
            rows = service.loadMyDiaryList(currentUserId);
        } catch (RuntimeException ex) {
            listContainer.getChildren().setAll(new Label("목록 조회 실패: " + ex.getMessage()));
            return;
        }

        listContainer.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("작성된 일기가 없습니다.");
            empty.setStyle("-fx-text-fill:#666; -fx-font-size:13;");
            listContainer.getChildren().add(empty);
            return;
        }

        for (DiaryEntry d : rows) {
            listContainer.getChildren().add(makeCard(d));
        }
    }

    /** 간단 카드 UI */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.setStyle("""
            -fx-background-color: white;
            -fx-padding: 14;
            -fx-background-radius: 10;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 8, 0, 0, 2);
        """);

        String dateStr = (d.getEntryDate() == null) ? "" : d.getEntryDate().format(DF);

        Label date = new Label("DATE " + dateStr);
        date.setStyle("-fx-text-fill:#333; -fx-font-weight:bold;");

        Label title = new Label("TITLE");
        title.setStyle("-fx-text-fill:#444;");

        Label content = new Label("CONTENTS " + (d.getDiaryContent() == null ? "" : d.getDiaryContent()));
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#222; -fx-font-size:13;");

        card.getChildren().addAll(date, title, content);
        return card;
    }
}
