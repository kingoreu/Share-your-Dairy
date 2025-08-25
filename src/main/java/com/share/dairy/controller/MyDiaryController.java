package com.share.dairy.controller;

import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;

// ===== [추가] 진행률 상태 파싱용 Jackson =====
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// ===== [추가] JavaFX UI 구성/게임/오버레이 관련 =====
import com.share.dairy.util.game.AvoidRocksPane; // ← 별도 파일로 분리된 '돌 피하기' 게임 컴포넌트
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.share.dairy.auth.UserSession.currentId;

/**
 * MyDiaryController (교체본)
 * ------------------------------------------------------------
 * - 일기 저장 → 분석 → (서버 트리거) 이미지 생성
 * - 생성 동안 '로딩 오버레이(진행률 바 + 돌 피하기 게임)' 표시
 * - 2초 폴링으로 /images/status 조회 → DONE 시 최종 완료 처리
 *
 * 백엔드 필요(이미 안내/구현함):
 *   POST /api/diary/{id}/images/auto      → 이미지 생성 비동기 시작
 *   GET  /api/diary/{id}/images/status    → {status, progress, message}
 */
public class MyDiaryController {

    /* 작성 화면 필드(있을 수도 있고 없을 수도 있음) */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    /* 목록 컨테이너(있으면 목록 모드) */
    @FXML private VBox listContainer;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    // ✅ 수정: 하드코딩 제거(=FK 오류 원인). 외부에서 로그인 유저 ID 주입받도록 함.


    // ===== 서버 URL/HTTP 클라이언트 =====
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

    // ===== [추가] 상태 폴링/오버레이 관련 필드 =====
    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService poller;
    private Stage loadingStage;
    private ProgressBar overlayProgress;
    private Label overlayPercent, overlayMsg;
    private AvoidRocksPane gamePane;

    // (옵션) 상태 API 없을 때 테스트용 가짜 진행률 모드
    private static final boolean FAKE_STATUS_MODE = false;
    private ScheduledFuture<?> fakeFuture;
    private int fakeProgress = 0;

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

    /**
     * SAVE: 일기 저장 → 분석 → (서버 트리거) 이미지 생성 → 오버레이+폴링 시작
     *
     * ⚠️ 변경 포인트:
     *   - 예전처럼 트리거 직후에 "완료" Alert를 즉시 띄우지 않는다.
     *   - 최종 Alert는 /status 가 DONE을 반환했을 때 띄운다.
     */
    @FXML
    private void onSave() {
        try {
            Long uid = currentId();
            String title   = (titleField  != null) ? titleField.getText().trim()  : "";
            String content = (contentArea != null) ? contentArea.getText().trim() : "";

            if (content.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "본문을 입력해 주세요.").showAndWait();
                return;
            }

            DiaryEntry entry = new DiaryEntry();
            entry.setUserId(uid);
            entry.setEntryDate(LocalDate.now());
            entry.setTitle(title);
            entry.setDiaryContent(content);
            entry.setVisibility(Visibility.PRIVATE);

            // DB 저장 (entry_id 획득)
            DiaryEntryDao dao = new DiaryEntryDao();
            long entryId = dao.save(entry);

            // 분석/이미지 생성 트리거는 백그라운드로
            new Thread(() -> {
                try {
                    // 1) GPT 분석
                    new DiaryAnalysisService().process(entryId);

                    // 2) 분석 완료 안내(비차단)
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "분석 완료! 키워드/캐릭터 이미지 생성을 시작합니다.").show()
                    );

                    // 3) 이미지 생성 시작(서버 트리거)
                    triggerAutoImage(entryId);

                    // 4) 로딩 오버레이 + 상태 폴링 시작
                    Platform.runLater(() -> showImageGenOverlayAndPoll(entryId));

                    // ⚠️ 최종 완료는 showImageGenOverlayAndPoll() 내부에서
                    //     /status = DONE 시점에 처리한다.

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
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/fxml/diary/my_diary/my_diary.fxml"));
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

        Long uid = currentId();
        if (uid == null|| uid <= 0) { // ✅ 로그인 이전에 불릴 수 있으니 가드
            listContainer.getChildren().setAll(new Label("로그인 후 내 일기를 볼 수 있어요."));
            return;
        }

        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(uid); // ✅ 내 것만
        } catch (RuntimeException ex) {
            listContainer.getChildren().setAll(new Label("일기 목록 조회 실패"));
            return;
        }

        listContainer.getChildren().clear();
        for (DiaryEntry d : rows) listContainer.getChildren().add(makeCard(d));
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
    // 이미지 자동 생성(서버 트리거)
    // =========================
    private void triggerAutoImage(long entryId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE_URL + "/api/diary/" + entryId + "/images/auto"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "이미지 자동 생성 실패: HTTP " + res.statusCode() + "\n" + res.body()
            );
        }
    }

    // =========================
    // [추가] 상태 조회 + 오버레이(게임) + 폴링
    // =========================

    /** 상태 조회: /api/diary/{id}/images/status */
    private JsonNode fetchImageStatus(long entryId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE_URL + "/api/diary/" + entryId + "/images/status"))
                .GET().build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("status HTTP " + res.statusCode() + " : " + res.body());
        }
        return mapper.readTree(res.body());
    }

    /** 로딩 오버레이 생성 + 2초 폴링 시작 */
    private void showImageGenOverlayAndPoll(long entryId) {
        // 이미 떠 있으면 재사용
        if (loadingStage != null && loadingStage.isShowing()) return;

        // ===== 오버레이 UI =====
        Label title = new Label("키워드/캐릭터 이미지 생성 중...");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        overlayProgress = new ProgressBar(-1); // 아직 진행률 모르면 indeterminate
        overlayProgress.setPrefWidth(420);

        overlayPercent = new Label("0%");
        overlayPercent.setTextFill(Color.WHITE);
        overlayPercent.setStyle("-fx-font-weight: bold;");

        overlayMsg = new Label("잠시만 기다려 주세요.");
        overlayMsg.setTextFill(Color.rgb(230,230,230));
        overlayMsg.setStyle("-fx-opacity: 0.92;");

        HBox prog = new HBox(10, overlayProgress, overlayPercent);
        prog.setAlignment(Pos.CENTER);

        // === 별도 파일로 분리된 '돌 피하기' 게임 삽입 ===
        gamePane = new AvoidRocksPane(520, 280);

        Button closeBtn = new Button("오버레이 닫기"); // 작업 취소 아님, UI만 닫기
        closeBtn.setOnAction(e -> { if (loadingStage != null) loadingStage.close(); });

        VBox box = new VBox(14, title, prog, overlayMsg, gamePane, closeBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setMaxWidth(600);
        box.setStyle("-fx-background-color: rgba(30,30,30,0.96); -fx-background-radius: 16;");

        StackPane root = new StackPane(box);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.45);");
        root.setPadding(new Insets(32));

        loadingStage = new Stage(StageStyle.TRANSPARENT);
        Stage owner = currentStage();
        if (owner != null) loadingStage.initOwner(owner);
        loadingStage.initModality(Modality.NONE); // 필요 시 APPLICATION_MODAL 로 변경
        loadingStage.setScene(new Scene(root, Color.TRANSPARENT));
        loadingStage.setTitle("이미지 생성 중…");

        // 창 닫힐 때 리소스 정리
        loadingStage.setOnCloseRequest(ev -> {
            stopPolling();
            stopFakeProgress();
            if (gamePane != null) gamePane.stop();
        });

        loadingStage.show();
        gamePane.requestGameFocus();

        // ===== 폴링 시작 (또는 FAKE 모드) =====
        if (FAKE_STATUS_MODE) {
            startFakeProgress(entryId);
            return;
        }

        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            try {
                JsonNode st = fetchImageStatus(entryId);
                String status = st.path("status").asText("RUNNING");
                int progress = st.path("progress").asInt(-1);
                String msg = st.path("message").asText("");

                Platform.runLater(() -> updateOverlay(progress, msg, status));

                if ("DONE".equalsIgnoreCase(status)) {
                    stopPolling();
                    Platform.runLater(() -> onImageDone(entryId));
                } else if ("ERROR".equalsIgnoreCase(status)) {
                    stopPolling();
                    Platform.runLater(this::onImageError);
                }
            } catch (Exception ex) {
                stopPolling();
                Platform.runLater(() -> {
                    if (loadingStage != null) loadingStage.close();
                    if (gamePane != null) gamePane.stop();
                    new Alert(Alert.AlertType.ERROR,
                            "상태 조회 중 오류: " + ex.getMessage()).showAndWait();
                });
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /** 진행률/메시지 UI 갱신 + 게임 배경 틴트 반영 */
    private void updateOverlay(int progress, String msg, String status) {
        if (progress >= 0) {
            overlayProgress.setProgress(progress / 100.0);
            overlayPercent.setText(progress + "%");
        } else {
            overlayProgress.setProgress(-1);
            overlayPercent.setText("");
        }
        overlayMsg.setText((msg == null || msg.isBlank()) ? ("상태: " + status) : msg);

        // 진행률에 따라 게임 배경을 조금 밝게
        if (gamePane != null && progress >= 0) gamePane.setProgressTint(progress);
    }

    /** DONE 처리: 오버레이 닫고 최종 Alert/콜백/리프레시/모달 닫기 */
    private void onImageDone(long entryId) {
        if (loadingStage != null) loadingStage.close();
        if (gamePane != null) gamePane.stop();

        new Alert(Alert.AlertType.INFORMATION,
                "일기 저장 및 분석/이미지 생성 완료!\nentry_id=" + entryId).showAndWait();

        if (onSaved != null) onSaved.accept(entryId);
        if (afterSave != null) afterSave.run();
        refreshList();

        if (dialogMode) {
            Stage st = currentStage();
            if (st != null) st.close();
        }
    }

    /** ERROR 처리 */
    private void onImageError() {
        if (loadingStage != null) loadingStage.close();
        if (gamePane != null) gamePane.stop();
        new Alert(Alert.AlertType.ERROR, "이미지 생성 실패").showAndWait();
    }

    /** 폴링 정지 */
    private void stopPolling() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    // ===== (옵션) 상태 API 없을 때 테스트용 가짜 진행률 =====
    private void startFakeProgress(long entryId) {
        stopFakeProgress();
        overlayProgress.setProgress(0);
        overlayPercent.setText("0%");
        overlayMsg.setText("샘플 상태: 시작");
        fakeProgress = 0;

        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        fakeFuture = ex.scheduleAtFixedRate(() -> {
            fakeProgress += 2; // 2%씩 증가 → ~100초
            Platform.runLater(() -> updateOverlay(fakeProgress, "샘플 상태: 진행 중", "RUNNING"));
            if (fakeProgress >= 100) {
                stopFakeProgress();
                Platform.runLater(() -> onImageDone(entryId));
            }
        }, 0, 2, TimeUnit.SECONDS);
        // 정리 편의상 poller로도 참조
        poller = ex;
    }

    private void stopFakeProgress() {
        if (fakeFuture != null) {
            fakeFuture.cancel(true);
            fakeFuture = null;
        }
    }
}