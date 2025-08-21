package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.app.music.MusicDialog; // ★ 추가: MUSIC 모달

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MyDiaryController {

    /* 작성 화면 필드(있을 수도 있고 없을 수도 있음) */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    /* 상단 네비에 MUSIC 버튼이 fx:id로 연결돼 있다면 사용 */
    @FXML private Button btnMusic; // ★ 선택적: 있으면 initialize에서 핸들러 연결

    /* 목록 컨테이너(있으면 목록 모드) */
    @FXML private VBox listContainer;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시

    /* 새 일기 모달 모드 & 저장 콜백(필요 시) */
    private boolean dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    /* 저장 후 후처리(메인 화면에서만 사용) */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML
    public void initialize() {
        // 작성 필드 활성화(있는 경우만)
        if (titleField != null)  titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);

        // 목록 모드면 목록 렌더
        if (listContainer != null) refreshList();

        // ★ MUSIC 버튼이 존재하면 클릭 시 모달 띄우기
        if (btnMusic != null) {
            btnMusic.setOnAction(e -> openMusicDialog());
        }
    }

    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }

    /* ★ 네비의 MUSIC 항목과 연결되어 있다면, 모달을 띄우도록 변경 */
    @FXML
    private void onMusic(){
        openMusicDialog();
    }

    @FXML private void onTime(){  if (timeField  != null) timeField.requestFocus();  }

    @FXML
    private void onEdit(){
        if (titleField != null)  titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
    }

    /** SAVE: 제목/내용 저장(제목 없으면 공백 저장) */
    @FXML
    private void onSave() {
        String content = (contentArea != null) ? contentArea.getText() : null;
        if (content == null || content.trim().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "내용을 입력하세요.").showAndWait();
            return;
        }
        String title = (titleField != null) ? titleField.getText() : null;

        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(currentUserId);
        entry.setEntryDate(java.time.LocalDate.now());
        entry.setDiaryContent(content.trim());
        entry.setTitle(title == null ? "" : title.trim());
        entry.setVisibility(Visibility.PRIVATE);
        entry.setSharedDiaryId(null);

        try {
            long newId = diaryWriteService.create(entry);
            new Alert(Alert.AlertType.INFORMATION, "저장 완료! (ID: " + newId + ")").showAndWait();

            // 모달에서 띄운 작성창이면: 콜백 → 모달 닫기
            if (dialogMode) {
                if (onSaved != null) onSaved.accept(newId);
                Stage st = currentStage();
                if (st != null) st.close();
                return;
            }

            // 메인 화면에서 저장한 경우: 목록 갱신 + 입력칸 초기화
            if (afterSave != null) afterSave.run();
            if (listContainer != null) refreshList();
            if (titleField != null)  titleField.clear();
            if (contentArea != null) contentArea.clear();

        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 목록 화면에서 연필(FAB) → 새 일기 모달 띄우기 */
    @FXML
    private void onClickFabPencil() throws IOException {
        // 프로젝트 구조에 맞춰 my_diary-view.fxml 사용
        FXMLLoader fxml = new FXMLLoader(getClass().getResource(
                "/fxml/diary/my_diary/my_diary-view.fxml"
        ));
        Parent root = fxml.load();

        MyDiaryController child = fxml.getController();
        child.setDialogMode(true);
        child.setOnSaved(id -> refreshList()); // 저장 후 목록 갱신

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
        listContainer.getChildren().clear();

        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(currentUserId);
        } catch (RuntimeException ex) {
            listContainer.getChildren().add(new Label("목록 조회 실패: " + deepestMessage(ex)));
            return;
        }

        for (DiaryEntry d : rows) {
            listContainer.getChildren().add(makeCard(d));
        }
    }

    /** 가장 안쪽 cause 메시지 추출 */
    private static String deepestMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return (c.getMessage() != null ? c.getMessage() : t.getMessage());
    }

    /** 제목이 비면 본문 앞부분으로 대체 */
    private static String guessTitle(DiaryEntry d) {
        String t = d.getTitle();
        if (t != null) t = t.trim();
        if (t != null && !t.isEmpty()) return t;

        String c = Optional.ofNullable(d.getDiaryContent()).orElse("");
        c = c.replace("\r"," ").replace("\n"," ").trim();
        if (c.isEmpty()) return "(제목 없음)";
        return c.length() > 30 ? c.substring(0,30) + "…" : c;
    }

    private static String preview(String s) {
        if (s == null) return "";
        String one = s.replace("\r"," ").replace("\n"," ").trim();
        return (one.length() > 60) ? one.substring(0, 60) + "…" : one;
    }

    /** 목록 카드 생성 + 클릭시 보기 모달 */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");

        String dateText = "DATE " + (d.getEntryDate() == null ? "" : DAY_FMT.format(d.getEntryDate()));
        Label date = new Label(dateText);
        Label title = new Label(guessTitle(d));
        Label content = new Label("CONTENTS " + preview(d.getDiaryContent()));

        card.getChildren().addAll(date, title, content);

        // 카드/내부 라벨 클릭 시 모달 열기(좌클릭)
        EventHandler<MouseEvent> opener = e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                e.consume();
                openDiaryViewer(d);
            }
        };
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, opener);
        date.addEventFilter(MouseEvent.MOUSE_CLICKED, opener);
        title.addEventFilter(MouseEvent.MOUSE_CLICKED, opener);
        content.addEventFilter(MouseEvent.MOUSE_CLICKED, opener);

        return card;
    }

    /** 읽기 전용 보기 모달 */
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

        Label date = new Label("DATE " + (d.getEntryDate() == null ? "" : DAY_FMT.format(d.getEntryDate())));
        Label title = new Label("TITLE " + guessTitle(d));

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

    /* ★ MUSIC 검색/재생 모달 열기 (공용) */
    private void openMusicDialog() {
        try {
            new MusicDialog().show();
        } catch (Throwable ex) {
            new Alert(Alert.AlertType.ERROR, "음악 검색창을 열 수 없습니다:\n" + (ex.getMessage() == null ? ex.toString() : ex.getMessage()))
                    .showAndWait();
        }
    }
}
