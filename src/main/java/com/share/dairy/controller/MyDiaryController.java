package com.share.dairy.controller;

import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.share.dairy.util.game.TetrisPane;
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

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;

import static com.share.dairy.auth.UserSession.currentId;

/**
 * MyDiaryController
 * - 일기 저장 → 분석 → 서버 트리거로 이미지 생성
 * - 진행 중 로딩 오버레이(진행률 + TetrisPane)
 * - /images/status 2초 폴링 → DONE 시 완료 처리
 */
public class MyDiaryController {

    /* 작성 화면 */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea contentArea;

    /* 목록 컨테이너(있으면 목록 모드) */
    @FXML private VBox listContainer;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();

    /* 서버/HTTP */
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /* 저장 후 후처리/콜백 */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    private boolean dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    /* 상태 폴링/오버레이 */
    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService poller;
    private Stage loadingStage;
    private ProgressBar overlayProgress;
    private Label overlayPercent, overlayMsg;
    private TetrisPane gamePane;

    /* (옵션) 가짜 진행률 */
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

    /** SAVE: 저장 → 분석 → 이미지 생성 트리거 → 오버레이+폴링 */
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

            // DB 저장
            DiaryEntryDao dao = new DiaryEntryDao();
            long entryId = dao.save(entry);

            // 분석/이미지 생성 트리거는 백그라운드
            new Thread(() -> {
                try {
                    new DiaryAnalysisService().process(entryId); // 분석
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "분석 완료! 키워드/캐릭터 이미지 생성을 시작합니다.").show()
                    );
                    triggerAutoImage(entryId);                    // 이미지 트리거
                    Platform.runLater(() -> showImageGenOverlayAndPoll(entryId)); // 오버레이+폴링
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

    /** 새 일기 모달 띄우기 */
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

    /** 목록 갱신 */
    private void refreshList() {
        if (listContainer == null) return;

        Long uid = currentId();
        if (uid == null || uid <= 0) {
            listContainer.getChildren().setAll(new Label("로그인 후 내 일기를 볼 수 있어요."));
            return;
        }

        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(uid);
        } catch (RuntimeException ex) {
            listContainer.getChildren().setAll(new Label("일기 목록 조회 실패"));
            return;
        }

        listContainer.getChildren().clear();
        for (DiaryEntry d : rows) listContainer.getChildren().add(makeCard(d));
    }

    /** 카드(클릭/키보드로 열기) */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color:white;-fx-background-radius:12;"
                + "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 3);");
        card.setPickOnBounds(true);
        card.setCursor(Cursor.HAND);
        card.setFocusTraversable(true);

        Label date = new Label("DATE " + Optional.ofNullable(d.getEntryDate()).orElse(null));
        date.setStyle("-fx-text-fill:#666;-fx-font-size:12;");

        String titleTxt = Optional.ofNullable(d.getTitle()).map(String::trim)
                .filter(s -> !s.isEmpty()).orElse("(제목 없음)");
        Label title = new Label("TITLE " + titleTxt);
        title.setStyle("-fx-font-size:15;-fx-font-weight:700;");

        String body = Optional.ofNullable(d.getDiaryContent()).orElse("");
        String preview = body.length() > 200 ? body.substring(0, 200) + "…" : body;
        Label content = new Label("CONTENTS " + preview);
        content.setWrapText(true);

        card.getChildren().addAll(date, title, content);

        EventHandler<MouseEvent> open = e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                openDiaryViewer(d);
                e.consume();
            }
        };
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, open);
        for (Node n : card.getChildren()) n.addEventHandler(MouseEvent.MOUSE_CLICKED, open);

        card.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER, SPACE -> openDiaryViewer(d);
            }
        });

        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + "-fx-background-color:#fff7fd;"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("-fx-background-color:#fff7fd;", "")));

        return card;
    }

    /** 읽기 전용 모달 */
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

    /* 이미지 자동 생성 트리거 */
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

    /* 상태 조회 */
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

    /** 로딩 오버레이 + 2초 폴링 시작 */
    private void showImageGenOverlayAndPoll(long entryId) {
        if (loadingStage != null && loadingStage.isShowing()) return;

        Label title = new Label("키워드/캐릭터 이미지 생성 중...");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        overlayProgress = new ProgressBar(-1);
        overlayProgress.setPrefWidth(420);

        overlayPercent = new Label("0%");
        overlayPercent.setTextFill(Color.WHITE);
        overlayPercent.setStyle("-fx-font-weight: bold;");

        overlayMsg = new Label("잠시만 기다려 주세요.");
        overlayMsg.setTextFill(Color.rgb(230,230,230));
        overlayMsg.setStyle("-fx-opacity: 0.92;");

        HBox prog = new HBox(10, overlayProgress, overlayPercent);
        prog.setAlignment(Pos.CENTER);

        // === 게임 ===
        gamePane = new TetrisPane(520, 280);

        // 닫기 버튼: 키 포커스/디폴트 비활성화 (Space/Enter가 버튼을 누르지 않도록)
        Button closeBtn = new Button("오버레이 닫기");
        closeBtn.setFocusTraversable(false);
        closeBtn.setDefaultButton(false);
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
        loadingStage.initModality(Modality.NONE);
        loadingStage.setScene(new Scene(root, Color.TRANSPARENT));
        loadingStage.setTitle("이미지 생성 중…");

        // ★ 포커스 회수: 창이 포커스를 얻을 때마다 게임으로 포커스
        loadingStage.focusedProperty().addListener((o, was, now) -> {
            if (now) Platform.runLater(() -> gamePane.requestGameFocus());
        });
        // ★ 어느 곳을 클릭해도 게임 포커스 회수
        root.setOnMouseClicked(ev -> gamePane.requestGameFocus());

        // 창 닫힐 때 정리
        loadingStage.setOnCloseRequest(ev -> {
            stopPolling();
            stopFakeProgress();
            if (gamePane != null) gamePane.stop();
        });

        loadingStage.show();
        Platform.runLater(() -> gamePane.requestGameFocus()); // 최초 포커스

        // 폴링
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

    /** 진행률/메시지 UI 갱신 + 게임 틴트 반영 */
    private void updateOverlay(int progress, String msg, String status) {
        if (progress >= 0) {
            overlayProgress.setProgress(progress / 100.0);
            overlayPercent.setText(progress + "%");
        } else {
            overlayProgress.setProgress(-1);
            overlayPercent.setText("");
        }
        overlayMsg.setText((msg == null || msg.isBlank()) ? ("상태: " + status) : msg);

        if (gamePane != null && progress >= 0) gamePane.setProgressTint(progress);
    }

    /** DONE 처리 */
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

    /** (옵션) 가짜 진행률 */
    private void startFakeProgress(long entryId) {
        stopFakeProgress();
        overlayProgress.setProgress(0);
        overlayPercent.setText("0%");
        overlayMsg.setText("샘플 상태: 시작");
        fakeProgress = 0;

        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        fakeFuture = ex.scheduleAtFixedRate(() -> {
            fakeProgress += 2;
            Platform.runLater(() -> updateOverlay(fakeProgress, "샘플 상태: 진행 중", "RUNNING"));
            if (fakeProgress >= 100) {
                stopFakeProgress();
                Platform.runLater(() -> onImageDone(entryId));
            }
        }, 0, 2, TimeUnit.SECONDS);
        poller = ex;
    }

    private void stopFakeProgress() {
        if (fakeFuture != null) {
            fakeFuture.cancel(true);
            fakeFuture = null;
        }
    }
}
