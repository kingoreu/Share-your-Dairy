package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MyDiaryController {

    /* 작성 화면 필드(있을 수도 있고 없을 수도 있음) */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    /* 목록 컨테이너(있으면 목록 모드) */
    @FXML private VBox listContainer;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시

    /* 저장 후 후처리(목록 갱신 등) */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    /* 새 일기 모달 모드 & 저장 콜백(필요 시) */
    private boolean dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    @FXML
    public void initialize() {
        if (titleField != null)  titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);

        if (listContainer != null) refreshList();
    }

    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }
    @FXML private void onMusic(){ if (musicField != null) musicField.requestFocus(); }
    @FXML private void onTime(){  if (timeField  != null) timeField.requestFocus();  }

    @FXML
    private void onEdit(){
        if (titleField != null)  titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
    }

    /** SAVE: 내용만 저장(제목은 나중에 처리) */
    @FXML
    private void onSave(){
        String content = (contentArea != null) ? contentArea.getText() : null;
        if (content == null || content.trim().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "내용을 입력하세요.").showAndWait();
            return;
        }

        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(currentUserId);
        entry.setEntryDate(LocalDate.now());

        if (titleField != null) {
            entry.setTitle(titleField.getText().trim());
        }

        entry.setDiaryContent(content.trim());
        entry.setVisibility(Visibility.PRIVATE);
        entry.setSharedDiaryId(null);

        try {
            long newId = diaryWriteService.create(entry);

            new Alert(Alert.AlertType.INFORMATION, "저장 완료! (ID: " + newId + ")").showAndWait();

            if (afterSave != null) afterSave.run();

            // 모달로 띄운 경우에만 그 모달 창 닫기 (메인창은 그대로)
            if (dialogMode) {
                if (onSaved != null) onSaved.accept(newId);
                Stage st = currentStage();
                if (st != null && st.getOwner() != null) st.close();
                return;
            }

            if (titleField != null)  titleField.setDisable(true);
            if (contentArea != null) contentArea.setDisable(true);
            if (listContainer != null) refreshList();

        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 목록 화면에서 연필(FAB) → 새 일기 모달 띄우기 */
    @FXML
    private void onClickFabPencil() throws IOException {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource(
                "/fxml/diary/my_diary/my_diary.fxml"
        ));
        Parent root = fxml.load();

        MyDiaryController child = fxml.getController();
        child.setDialogMode(true);
        child.setOnSaved(id -> refreshList());

        Stage dlg = new Stage();
        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner(listContainer.getScene().getWindow());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("New Diary");
        dlg.setScene(new Scene(root));
        dlg.showAndWait();

        refreshList();
    }

    /** 목록 렌더 */
    private void refreshList() {
        if (listContainer == null) return;
        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(currentUserId);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "일기 목록 조회 실패").showAndWait();
            return;
        }

        listContainer.getChildren().clear();
        for (DiaryEntry d : rows) {
            listContainer.getChildren().add(makeCard(d));
        }
    }

    /** 카드: 단순 표시(클릭 동작 없음 — 안정 상태) */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");
        Label date = new Label("DATE " + Optional.ofNullable(d.getEntryDate()).orElse(null));
        Label title = new Label("TITLE" + Optional.ofNullable(d.getTitle()).orElse("")); // 제목은 나중에
        Label content = new Label("CONTENTS " + Optional.ofNullable(d.getDiaryContent()).orElse(""));
        card.getChildren().addAll(date, title, content);
        return card;
    }

    /** 읽기 전용 모달 (나중용) */
    private void openDiaryViewer(DiaryEntry d) {
        Stage dlg = new Stage();

        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner(listContainer.getScene().getWindow());
        } else {
            Stage st = currentStage();
            if (st != null) dlg.initOwner(st);
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Diary");

        String dateText = "DATE " + Optional.ofNullable(d.getEntryDate()).orElse(null);
        String titleText = "TITLE " + Optional.ofNullable(d.getTitle())
                .map(String::trim).filter(s -> !s.isEmpty())
                .orElse("제목 없음");

        Label date = new Label(dateText);
        Label title = new Label(titleText);

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

    private Stage currentStage() {
        if (titleField != null && titleField.getScene() != null) {
            return (Stage) titleField.getScene().getWindow();
        }
        if (contentArea != null && contentArea.getScene() != null) {
            return (Stage) contentArea.getScene().getWindow();
        }
        return null;
    }
}
