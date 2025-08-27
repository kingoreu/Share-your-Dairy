package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.service.diary.DiaryWriteService;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import static com.share.dairy.auth.UserSession.currentId;

public class HubListController {

    @FXML private VBox listContainer;

    private final DiaryWriteService service = new DiaryWriteService();

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML
    private void initialize() {
        // 리스트 컨테이너를 카드 레이아웃처럼 보이도록
        if (listContainer != null) {
            listContainer.setSpacing(16);
            listContainer.setPadding(new Insets(18));
            if (listContainer.getParent() instanceof ScrollPane sp) sp.setFitToWidth(true);
        }
        refreshList();
    }

    /** DB → 카드 렌더 (MY DIARY: shared_diary_id IS NULL 만 가져오도록 서비스가 필터링) */
    public void refreshList() {
        List<DiaryEntry> rows;
        try {
            rows = service.loadMyDiaryList(currentId());
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

        rows.forEach(d -> listContainer.getChildren().add(makeCard(d)));
    }

    /** 카드 UI + 클릭 시 뷰어 모달 */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(card, new Insets(0, 0, 8, 0));
        card.setStyle(
            "-fx-background-color:white;" +
            "-fx-background-radius:12;" +
            "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.10), 10, 0, 0, 4);"
        );
        card.setCursor(Cursor.HAND);

        String dateStr = (d.getEntryDate() == null) ? "" : d.getEntryDate().format(DF);
        Label date = new Label("DATE " + dateStr);
        date.setStyle("-fx-text-fill:#666; -fx-font-size:12; -fx-font-weight:bold;");

        String titleStr = Optional.ofNullable(d.getTitle()).map(String::trim)
                .filter(s -> !s.isEmpty()).orElse("(제목 없음)");
        Label title = new Label("TITLE " + titleStr);
        title.setStyle("-fx-text-fill:#2d2150; -fx-font-size:15; -fx-font-weight:700;");

        String body = Optional.ofNullable(d.getDiaryContent()).orElse("");
        if (body.length() > 240) body = body.substring(0, 240) + "…";
        Label content = new Label("CONTENTS " + body);
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#333; -fx-font-size:13;");

        card.getChildren().addAll(date, title, content);

        // 클릭(좌클릭) → 모달 뷰어
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) openViewer(d);
        });

        // Hover 느낌
        card.setOnMouseEntered(e ->
            card.setStyle(card.getStyle() + "-fx-background-color:#fff7fd;"));
        card.setOnMouseExited(e ->
            card.setStyle(card.getStyle().replace("-fx-background-color:#fff7fd;", "")));

        return card;
    }

    /** 읽기 전용 모달 */
    private void openViewer(DiaryEntry d) {
        Stage dlg = new Stage();
        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner((Stage) listContainer.getScene().getWindow());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("MY DIARY");

        Label date = new Label("DATE " + (d.getEntryDate() == null ? "" : d.getEntryDate().format(DF)));
        date.setStyle("-fx-text-fill:#666; -fx-font-size:12;");

        Label title = new Label(Optional.ofNullable(d.getTitle()).filter(s -> !s.isBlank()).orElse("(제목 없음)"));
        title.setStyle("-fx-font-size:17; -fx-font-weight:800;");

        TextArea body = new TextArea(Optional.ofNullable(d.getDiaryContent()).orElse(""));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);

        Button close = new Button("닫기");
        close.setOnAction(ev -> dlg.close());

        VBox root = new VBox(10, date, title, body, close);
        root.setPadding(new Insets(16));
        dlg.setScene(new Scene(root, 640, 480));
        dlg.showAndWait();
    }
}
