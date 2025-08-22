package com.share.dairy.controller;

import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;
import javafx.application.Platform;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    // ✅ 수정: 하드코딩 제거(=FK 오류 원인). 외부에서 로그인 유저 ID 주입받도록 함.
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시

    // ✅ 추가: 서버 URL/HTTP 클라이언트 (이미지 자동 생성 REST 호출용)
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
    private void onSave() {
        try {

            String title   = (titleField  != null) ? titleField.getText().trim()  : "";
            String content = (contentArea != null) ? contentArea.getText().trim() : "";
            if (content.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "본문을 입력해 주세요.").showAndWait();
                return;
            }

            DiaryEntry entry = new DiaryEntry();
            entry.setUserId(currentUserId);
            entry.setEntryDate(LocalDate.now());
            entry.setTitle(title);
            entry.setDiaryContent(content);
            entry.setVisibility(Visibility.PRIVATE); // ✅ enum으로 설정

            DiaryEntryDao dao = new DiaryEntryDao();
            long entryId = dao.save(entry);

            // 저장 직후 분석(백그라운드 실행: UI 멈춤 방지)
            new Thread(() -> {
                try {
                    new DiaryAnalysisService().process(entryId);

                    // ✅ 추가: “분석 완료 → 이미지 생성 시작” 비차단 알림
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "분석 완료! 키워드/캐릭터 이미지 생성을 시작합니다.").show()
                    );


                    // ✅ 추가: 이미지 자동 생성 트리거 (서버 REST: POST /api/diary/{id}/images/auto)
                    triggerAutoImage(entryId);

                    // ✅ 변경: 최종 완료 알림(이미지까지 완료)
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "일기 저장 및 분석/이미지 생성 완료!\nentry_id=" + entryId).showAndWait()
                    );


                    // ✅ 추가: 콜백 호출들 — 목록 리프레시/허브 전환 등
                    if (onSaved != null) Platform.runLater(() -> onSaved.accept(entryId));
                    if (afterSave != null) Platform.runLater(afterSave);

                    // ✅ 추가: 모달로 열렸다면 닫아주기
                    if (dialogMode) {
                        Platform.runLater(() -> {
                            Stage st = currentStage();
                            if (st != null) st.close();
                        });
                    }

                } catch (Exception ex) {
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR,
                                    "분석/이미지 생성 중 오류: " + ex.getMessage()).showAndWait()
                    );
                }
            }).start();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "저장 중 오류: " + e.getMessage()).showAndWait();
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
            rows = diaryWriteService.loadMyDiaryList(
                    Optional.ofNullable(currentUserId).orElse(0L)  // ✅ NPE 방지
            );
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
    // =========================
    // ✅ 추가: 이미지 자동 생성 호출/폴링 유틸
    // =========================
    private void triggerAutoImage(long entryId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE_URL + "/api/diary/" + entryId + "/images/auto"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "이미지 자동 생성 실패: HTTP " + res.statusCode() + "\n" + res.body());
        }
    }
}