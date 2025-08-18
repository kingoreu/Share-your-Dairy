package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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

    /* ---------- 작성 화면 필드(없을 수도 있으니 null-guard) ---------- */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    /* ---------- 목록 화면 컨테이너(없으면 목록 모드 아님) ---------- */
    @FXML private VBox listContainer;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시

    /* ---------- 새 일기 다이얼로그 모드 제어 & 저장 콜백 ---------- */
    private boolean dialogMode = false;                 // 새 일기 창인지 여부
    private Consumer<Long> onSaved;                     // 저장 성공 시 알림용
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    @FXML
    public void initialize() {
        // 작성 화면이면 에디트 가능
        if (titleField != null) titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);

        // 목록 화면이면 목록 로드
        if (listContainer != null) refreshList();
    }

    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }
    @FXML private void onMusic(){ if (musicField != null) musicField.requestFocus(); }
    @FXML private void onTime(){  if (timeField  != null) timeField.requestFocus();  }

    @FXML
    private void onEdit(){
        if (titleField != null) titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
    }

    /** SAVE: 현재 스키마 기준으로 본문만 저장 */
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
        entry.setDiaryContent(content.trim());
        entry.setVisibility(Visibility.PRIVATE);
        entry.setSharedDiaryId(null);

        try {
            long newId = diaryWriteService.create(entry);

            // 다이얼로그 모드면 호출자에게 알리고 창 닫기
            if (dialogMode) {
                if (onSaved != null) onSaved.accept(newId);
                closeWindow();
                return;
            }

            // 단독 화면이면 안내만
            new Alert(Alert.AlertType.INFORMATION, "저장 완료! (ID: " + newId + ")").showAndWait();
            if (titleField != null) titleField.setDisable(true);
            if (contentArea != null) contentArea.setDisable(true);
            if (listContainer != null) refreshList();

        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 목록 화면: 연필(FAB)로 새 일기 창 띄우기 */
    @FXML
    private void onClickFabPencil() throws IOException {
        // ★ 새 일기 FXML 경로: 프로젝트 구조에 맞게 확인
        FXMLLoader fxml = new FXMLLoader(getClass().getResource(
            "/fxml/diary/my_diary/my_diary.fxml"
        ));
        Parent root = fxml.load();

        // 동일 컨트롤러 타입(새 인스턴스)에 다이얼로그 모드/콜백 주입
        MyDiaryController child = fxml.getController();
        child.setDialogMode(true);
        child.setOnSaved(id -> refreshList());

        Stage dlg = new Stage();
        // 목록 화면에서만 가능한 안전한 소유자 지정
        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner(listContainer.getScene().getWindow());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("New Diary");
        dlg.setScene(new Scene(root));
        dlg.showAndWait();

        // 혹시 사용자가 취소했더라도 보수적으로 한번 더 갱신
        if (listContainer != null) refreshList();
    }

    /** DB에서 목록 읽어와 카드 렌더 */
    private void refreshList() {
        if (listContainer == null) return; // 목록 화면이 아님
        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(currentUserId);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "목록 조회 실패: " + ex.getMessage()).showAndWait();
            return;
        }

        listContainer.getChildren().clear();
        for (DiaryEntry d : rows) {
            listContainer.getChildren().add(makeCard(d));
        }
    }

    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");
        Label date = new Label("DATE " + Optional.ofNullable(d.getEntryDate()).orElse(null));
        Label title = new Label("TITLE");
        Label content = new Label("CONTENTS  " + Optional.ofNullable(d.getDiaryContent()).orElse(""));
        card.getChildren().addAll(date, title, content);
        return card;
    }

    private void closeWindow() {
        // 현재 컨트롤러가 붙어 있는 창 닫기 (작성 FXML에서만 호출)
        if (titleField != null && titleField.getScene() != null) {
            Stage st = (Stage) titleField.getScene().getWindow();
            st.close();
        } else if (contentArea != null && contentArea.getScene() != null) {
            Stage st = (Stage) contentArea.getScene().getWindow();
            st.close();
        }
    }
}
