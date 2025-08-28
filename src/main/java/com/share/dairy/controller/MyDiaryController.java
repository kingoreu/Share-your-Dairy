package com.share.dairy.controller;

import com.share.dairy.app.music.MusicDialog;           // ← [추가] 음악 검색 모달
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;

// ===== [추가] 진행률 상태 파싱용 Jackson =====
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// ===== [추가] JavaFX UI 구성/게임/오버레이 관련 =====
import com.share.dairy.util.game.TetrisPane;
import javafx.application.Platform;
import javafx.concurrent.Worker;                  // ← [추가] WebView 로드 상태
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;               // ← [추가]
import javafx.scene.web.WebView;                // ← [추가]
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Desktop;                        // ← [추가] “유튜브로 열기”
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * MyDiaryController (교체본)
 * ------------------------------------------------------------
 * - 일기 저장 → 분석 → (서버 트리거) 이미지 생성
 * - 생성 동안 '로딩 오버레이(진행률 바 + 돌 피하기 게임)' 표시
 * - 2초 폴링으로 /images/status 조회 → DONE 시 최종 완료 처리
 *
 * 백엔드 필요(이미 안내/구현함):
 *   POST /api/diary/{id}/images/auto      → 이미지 생성 비동기 시작
 *   GET  /api/diary/{id}/images/status    → {status, progress, message}
 *
 * [추가됨 — 유튜브 음악]
 * - NEW DIARY: MUSIC 검색 → Place 아래 인라인 BGM 바 표시 (+ 저장 시 attachments에 URL 저장)
 * - MY DIARY(뷰어): 저장된 BGM URL을 읽어 하단 인라인 바 자동 표시(검색 없이 바로)
 * - ‘유튜브로 열기’, ‘펼치기’(인라인 작은 카드 WebView), 🔈/🔇 토글 지원
 */
public class MyDiaryController {

    /* ================== 작성 화면 필드 ================== */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea  contentArea;
    @FXML private VBox      listContainer;

    /* ========== MUSIC 패널/미니바(FXML에 있다면 연결) ========== */
    @FXML private Button   btnMusic;
    @FXML private HBox     musicBar;
    @FXML private WebView  musicWeb;
    @FXML private Label    musicTitle, musicChannel;
    @FXML private Hyperlink musicOpenLink;
    @FXML private HBox     musicMini;
    @FXML private Button   musicMiniToggle;
    @FXML private Button   musicMuteBtn;

    /* ========== 인라인 BGM 바(Place 아래 동적 삽입) ========== */
    private VBox inlineContainer;          // 컨트롤행 + (접힘) 미니 카드
    private HBox inlineCtrlRow;            // 🔈 BGM 재생중 | 유튜브로 열기 | 펼치기
    private HBox miniCardRow;              // 작은 WebView 카드
    private WebView miniWeb;               // 카드 내부 재생용
    private Label miniTitleLbl, miniChannelLbl;
    private Hyperlink miniOpenLink;
    private Button inlineExpandBtn;

    private boolean miniVisible = false;   // 카드 접힘 상태
    private boolean isMuted = false;       // 🔈/🔇 상태
    private String pendingMusicUrl;        // NEW DIARY에서 선택 후 저장 전까지 기억

    /* ========== 상태/서비스 공통 ========== */
    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    private boolean dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    /* ===== [추가] 상태 폴링/오버레이 관련 필드 ===== */
    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService poller;
    private Stage loadingStage;
    private ProgressBar overlayProgress;
    private Label overlayPercent, overlayMsg;
    private TetrisPane gamePane;

    // (옵션) 상태 API 없을 때 테스트용 가짜 진행률 모드
    private static final boolean FAKE_STATUS_MODE = false;
    private ScheduledFuture<?> fakeFuture;
    private int fakeProgress = 0;

    // (플레이어 상태 — 기존 패널용)
    private String  currentVideoId, currentVideoUrl;
    private boolean minimizeOnReady = false;
    private boolean playerReady     = false;

    @FXML
    public void initialize() {
        if (titleField != null)  titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
        if (listContainer != null) refreshList();

        // MUSIC 버튼: 검색 모달
        if (btnMusic != null) btnMusic.setOnAction(e -> openMusicDialog());

        // 패널/미니바 기본 숨김(기존 큰 패널은 필요 시만)
        showPanel(false);
        if (musicWeb != null) {
            musicWeb.getEngine().setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            );
        }
        syncMuteButton();
    }

    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }
    @FXML private void onMusic(){ openMusicDialog(); }
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
     * [추가]
     *   - pendingMusicUrl 이 있으면 diary_attachments에 LINK로 저장
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

            // [추가] BGM URL 저장 (있을 때만)
            try {
                if (pendingMusicUrl != null && !pendingMusicUrl.isBlank()) {
                    try (var con = com.share.dairy.util.DBConnection.getConnection()) {
                        var att = new com.share.dairy.model.diary.DiaryAttachment();
                        att.setEntryId(entryId);
                        try { // enum이 있으면 LINK 사용
                            att.setAttachmentType(com.share.dairy.model.enums.AttachmentType.valueOf("LINK"));
                        } catch (IllegalArgumentException ignore) {
                            att.setAttachmentType(null);
                        }
                        att.setPathOrUrl(pendingMusicUrl);
                        att.setDisplayOrder(1);
                        new com.share.dairy.dao.diary.DiaryAttachmentDao().insert(con, att);
                    }
                }
            } catch (Exception ex) {
                System.err.println("[BGM] URL 저장 실패: " + ex.getMessage());
            } finally {
                pendingMusicUrl = null; // 한 번 저장했으면 비움
            }

            // (분석/이미지 생성은 필요 시 다시 활성화)
            new Thread(() -> {
                try {
                    new DiaryAnalysisService().process(entryId);
                    Platform.runLater(() ->
                        new Alert(Alert.AlertType.INFORMATION,
                            "분석 완료! 키워드/캐릭터 이미지 생성을 시작합니다.").show()
                    );
                    triggerAutoImage(entryId);
                    Platform.runLater(() -> showImageGenOverlayAndPoll(entryId));
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

    /** 카드: 단순 표시 + 클릭 시 뷰어 열기 */
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

        card.setOnKeyPressed(e -> { switch (e.getCode()) { case ENTER, SPACE -> openDiaryViewer(d); } });

        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + "-fx-background-color:#fff7fd;"));
        card.setOnMouseExited (e -> card.setStyle(card.getStyle().replace("-fx-background-color:#fff7fd;", "")));

        return card;
    }

    /** 읽기 전용 모달 (MY DIARY 카드 클릭) — 저장된 BGM을 하단에 자동 표시 */
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

        // === 하단 인라인 바: 저장된 BGM URL이 있으면 바로 삽입 ===
        VBox inline = buildInlineBarForViewer(resolveSavedMusicUrl(d.getEntryId()));
        VBox root = (inline == null)
                ? new VBox(10, date, title, body, closeBtn(dlg))
                : new VBox(10, date, title, body, inline, closeBtn(dlg));
        root.setPadding(new Insets(16));

        dlg.setScene(new Scene(root, 720, 520));
        dlg.showAndWait();
    }

    private Button closeBtn(Stage dlg){
        Button b = new Button("닫기");
        b.setOnAction(ev -> dlg.close());
        return b;
    }

    private Stage currentStage() {
        if (titleField != null && titleField.getScene() != null)
            return (Stage) titleField.getScene().getWindow();
        if (contentArea != null && contentArea.getScene() != null)
            return (Stage) contentArea.getScene().getWindow();
        return null;
    }

    // ========================= 이미지 자동 생성(서버 트리거) =========================
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

    // ========================= [추가] 상태 조회 + 오버레이(게임) + 폴링 =========================
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

        gamePane = new TetrisPane(520, 280);

        Button closeBtn = new Button("오버레이 닫기");
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

        loadingStage.setOnCloseRequest(ev -> {
            stopPolling();
            stopFakeProgress();
            if (gamePane != null) gamePane.stop();
        });

        loadingStage.show();
        gamePane.requestGameFocus();

        if (FAKE_STATUS_MODE) { startFakeProgress(entryId); return; }

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

    private void onImageError() {
        if (loadingStage != null) loadingStage.close();
        if (gamePane != null) gamePane.stop();
        new Alert(Alert.AlertType.ERROR, "이미지 생성 실패").showAndWait();
    }

    private void stopPolling() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

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

    // =====================================================================
    // ===================== [추가] 유튜브 검색/재생 =========================
    // =====================================================================

    /** MUSIC 버튼 → 검색 모달 → 선택 시 인라인 바 표시 + 유튜브 자동 오픈(1회) */
    private void openMusicDialog() {
        try {
            new MusicDialog(item -> {
                if (item == null) return;
                String vid = item.videoId();
                if (vid == null || vid.isBlank()) return;

                pendingMusicUrl = (item.url() != null && !item.url().isBlank())
                        ? item.url()
                        : ("https://www.youtube.com/watch?v=" + vid);

                // 인라인 바 표시
                mountInlineBgmBar("♪  BGM 재생중", pendingMusicUrl);
                // 메타 있으면 카드 우측 텍스트 갱신
                if (miniTitleLbl != null)   miniTitleLbl.setText(item.title());
                if (miniChannelLbl != null) miniChannelLbl.setText(item.channel() == null ? "" : item.channel());

                // ✅ 선택 직후 유튜브를 외부 브라우저로 한 번 자동 오픈
                openYoutubeOnce(pendingMusicUrl);
            }).show(); // 모달이면 showAndWait() 사용 가능
        } catch (Throwable ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "음악 검색창을 열 수 없습니다:\n" + (ex.getMessage() == null ? ex.toString() : ex.getMessage()))
                    .showAndWait();
        }
    }

    // 선택된 곡을 외부 브라우저로 한 번 자동 오픈 (백그라운드에서 안전하게)
    private void openYoutubeOnce(String url) {
        if (url == null || url.isBlank()) return;
        new Thread(() -> openExternal(url), "open-youtube").start();
    }

    // 최적 시도: Desktop.browse → OS별 명령어로 대체 → 최후엔 클립보드 복사 안내
    private void openExternal(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                var d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    d.browse(java.net.URI.create(url));
                    return;
                }
            }
        } catch (Exception ignore) { /* fallthrough */ }

        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start(); return;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start(); return;
            } else {
                new ProcessBuilder("xdg-open", url).start(); return;
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                try {
                    var content = new javafx.scene.input.ClipboardContent();
                    content.putString(url);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                } catch (Exception ignored) {}
                new Alert(Alert.AlertType.INFORMATION,
                        "브라우저를 자동으로 열 수 없어 링크를 클립보드에 복사했습니다.\nCtrl+V로 붙여넣어 열어주세요.\n" + url
                ).show();
            });
        }
    }

    /* ===================== 인라인 바(컨트롤행 + 미니카드) ===================== */

    /** Place 아래 인라인 바(NEW DIARY) — 동적 생성/갱신 (WebView 지연 생성) */
private void mountInlineBgmBar(String labelText, String url) {
    if (placeField == null) return;
    Parent parent = placeField.getParent();
    if (!(parent instanceof Pane pane)) return;

    // 최초 생성: 컨테이너(VBox) + 컨트롤행 + (비어있는) 카드행
    if (inlineContainer == null) {
        // 컨트롤 행 -------------------------------------------------
        Button mute = new Button(isMuted ? "🔇" : "🔈");
        mute.setMinWidth(36);
        mute.setOnAction(e -> { toggleMute(); mute.setText(isMuted ? "🔇" : "🔈"); applyMuteJSOnMini(); });

        Label lbl = new Label(labelText == null ? "♪  BGM 재생중" : labelText);

        Hyperlink open = new Hyperlink("유튜브로 열기");
        open.setOnAction(e -> safeBrowse(url));

        inlineExpandBtn = new Button("펼치기");
        inlineExpandBtn.setOnAction(e -> toggleMiniCard(url)); // 펼치기 눌러야 WebView 생성

        inlineCtrlRow = new HBox(18, mute, lbl, open, inlineExpandBtn);
        inlineCtrlRow.setAlignment(Pos.CENTER_LEFT);

        // 미니 카드(처음엔 비어 있고 감춤) -----------------------------
        miniCardRow = new HBox(12);
        miniCardRow.setAlignment(Pos.CENTER_LEFT);
        miniCardRow.setPadding(new Insets(6, 8, 8, 8));
        miniCardRow.setStyle("-fx-background-color:white; -fx-background-radius:12; "
                + "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.10), 8, 0, 0, 3);");
        miniCardRow.setManaged(false);
        miniCardRow.setVisible(false);
        miniVisible = false;

        inlineContainer = new VBox(8, inlineCtrlRow, miniCardRow);
        inlineContainer.setPadding(new Insets(10, 12, 10, 12));
        inlineContainer.setStyle("-fx-background-color:#f6d4e4;-fx-background-radius:10;");

        // Place 바로 아래에 삽입
        if (pane instanceof VBox vb) {
            int idx = vb.getChildren().indexOf(placeField);
            if (idx >= 0) vb.getChildren().add(idx + 1, inlineContainer);
            else vb.getChildren().add(inlineContainer);
        } else {
            pane.getChildren().add(inlineContainer);
        }
    } else {
        // 갱신: 라벨/링크/버튼 핸들러만 업데이트
        ((Label) inlineCtrlRow.getChildren().get(1))
                .setText(labelText == null ? "♪  BGM 재생중" : labelText);
        ((Hyperlink) inlineCtrlRow.getChildren().get(2))
                .setOnAction(e -> safeBrowse(url));
        inlineExpandBtn.setOnAction(e -> toggleMiniCard(url));
        if (miniOpenLink != null) miniOpenLink.setOnAction(e -> safeBrowse(url));
    }

    // 이미 펼쳐져 있으면(=보이는 상태면) 컨텐츠 로드/리로드
    if (miniVisible) ensureMiniLoaded(url);
    }

    // [교체] 뷰어용 — 펼치기 전에는 WebView 만들지 않음
    private VBox buildInlineBarForViewer(String url) {
    if (url == null || url.isBlank()) return null;

    Button mute = new Button(isMuted ? "🔇" : "🔈");
    mute.setMinWidth(36);
    mute.setOnAction(e -> { toggleMute(); mute.setText(isMuted ? "🔇" : "🔈"); });

    Label lbl = new Label("♪  외부 브라우저 재생");
    Hyperlink open = new Hyperlink("유튜브로 열기");
    open.setOnAction(e -> safeBrowse(url));

    Button expand = new Button("펼치기");

    HBox card = new HBox(12);
    card.setAlignment(Pos.CENTER_LEFT);
    card.setPadding(new Insets(6, 8, 8, 8));
    card.setStyle("-fx-background-color:white; -fx-background-radius:12; "
            + "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.10), 8, 0, 0, 3);");
    card.setManaged(false);
    card.setVisible(false);

    expand.setOnAction(e -> {
        boolean show = !card.isVisible();
        card.setVisible(show);
        card.setManaged(show);
        expand.setText(show ? "접기" : "펼치기");
        if (show && card.getChildren().isEmpty()) {
            WebView vw = safeNewWebView();
            if (vw != null) {
                vw.setPrefSize(320, 180);
                vw.setMinSize(320, 180);
                vw.setMaxSize(320, 180);
                Label t = new Label("BGM 미리보기"); t.setStyle("-fx-font-size:13; -fx-font-weight:700;");
                Label ch = new Label("");
                Hyperlink link = new Hyperlink("유튜브에서 열기");
                link.setOnAction(ev -> safeBrowse(url));
                VBox right = new VBox(4, t, ch, link);
                right.setAlignment(Pos.TOP_LEFT);
                card.getChildren().setAll(vw, right);
                String vid = extractVideoId(url);
                if (vid != null) vw.getEngine().loadContent(miniPlayerHtml(vid), "text/html");
                try { if (vw != null) vw.getEngine().executeScript(isMuted ? "__mute()" : "__unmute()"); } catch (Exception ignore) {}
            } else {
                Label fallback = new Label("이 환경에서는 미리보기를 사용할 수 없습니다.");
                fallback.setStyle("-fx-text-fill:#555;");
                card.getChildren().setAll(fallback);
                expand.setDisable(true);
            }
        }
    });

    HBox ctrl = new HBox(18, mute, lbl, open, expand);
    ctrl.setAlignment(Pos.CENTER_LEFT);

    VBox box = new VBox(8, ctrl, card);
    box.setPadding(new Insets(10, 12, 10, 12));
    box.setStyle("-fx-background-color:#f6d4e4;-fx-background-radius:10;");
    return box;
    }

    private void toggleMiniCard(String url){
        miniVisible = !miniVisible;
        miniCardRow.setVisible(miniVisible);
        miniCardRow.setManaged(miniVisible);
        inlineExpandBtn.setText(miniVisible ? "접기" : "펼치기");
        if (miniVisible) ensureMiniLoaded(url);
    }

    private void ensureMiniLoaded(String url){
        String vid = extractVideoId(url);
        if (vid != null && miniWeb.getEngine().getLoadWorker().getState() != Worker.State.SUCCEEDED) {
            miniWeb.getEngine().loadContent(miniPlayerHtml(vid), "text/html");
            applyMuteJSOnMini();
        }
    }

    private void safeBrowse(String url) {
        try { if (url != null && !url.isBlank()) Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ignore) {}
    }

    /* ====== 인라인 미니 플레이어 HTML ====== */
    private String miniPlayerHtml(String videoId){
        String vid = videoId == null ? "" : videoId;
        return """
          <!doctype html><html><head><meta charset="utf-8"></head>
          <body style="margin:0;background:#000">
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              var player;
              function onYouTubeIframeAPIReady(){
                player = new YT.Player('player', {
                  height:'180', width:'320',
                  videoId:'%s',
                  playerVars:{ 'autoplay':1, 'rel':0, 'modestbranding':1,
                    'playsinline':1, 'loop':1, 'playlist':'%s', 'enablejsapi':1 },
                  events:{
                    'onReady': function(e){ try{e.target.playVideo();}catch(_){};
                        document.title='YTRDY'; },
                    'onError': function(e){ try{ document.title='YTERR:'+e.data; }catch(_){ } }
                  }
                });
              }
              function __mute(){ try{ if(player) player.mute(); }catch(e){} }
              function __unmute(){ try{ if(player) player.unMute(); }catch(e){} }
            </script>
          </body></html>
        """.formatted(vid, vid);
    }

    /* ===================== 기존 큰 패널(필요 시 유지) ===================== */

    /** 패널에서 YouTube 임베드 재생(반복, autoMinimize 지원, 임베드 금지는 링크로 폴백) */
    private void playInPanel(String videoId, String title, String channel, String url, boolean autoMinimize) {
        if (musicWeb == null || musicBar == null) return;

        currentVideoId  = videoId;
        currentVideoUrl = url;

        if (musicTitle    != null) musicTitle.setText(title   == null ? "" : title);
        if (musicChannel  != null) musicChannel.setText(channel== null ? "" : channel);
        if (musicOpenLink != null) musicOpenLink.setVisible(url != null && !url.isBlank());

        String html = playerHtml(videoId);
        WebEngine eng = musicWeb.getEngine();

        playerReady = false;
        isMuted = false;            // 새 재생 시 기본 음소거 해제
        minimizeOnReady = autoMinimize;
        syncMuteButton();

        eng.getLoadWorker().stateProperty().addListener((obs, old, st) -> {
            if (st == Worker.State.SUCCEEDED) {
                playerReady = true;
                if (minimizeOnReady) {
                    minimizeOnReady = false;
                    showMini(true);
                }
                applyMuteJS();
            } else if (st == Worker.State.FAILED) {
                eng.load("https://www.youtube.com/watch?v=" + videoId);
                showMini(true);
                applyMuteJS();
            }
        });

        eng.titleProperty().addListener((o, ov, nv) -> {
            if (nv != null && nv.startsWith("YTERR:")) {
                eng.load("https://www.youtube.com/watch?v=" + videoId);
                showMini(true);
                applyMuteJS();
            }
        });

        eng.loadContent(html, "text/html");
        showPanel(true);            // 우선 펼친 상태로 로드
    }

    private String playerHtml(String videoId) {
        String vid = videoId == null ? "" : videoId;
        return """
            <!doctype html><html><head><meta charset="utf-8"></head>
            <body style="margin:0;background:#000">
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player;
                function onYouTubeIframeAPIReady(){
                  player = new YT.Player('player', {
                    height:'160', width:'284',
                    videoId:'%s',
                    playerVars:{
                      'autoplay':1, 'rel':0, 'modestbranding':1,
                      'playsinline':1, 'loop':1, 'playlist':'%s',
                      'enablejsapi':1
                    },
                    events:{
                      'onReady': function(e){ try{e.target.playVideo();}catch(_){}; document.title='YTRDY'; },
                      'onError': function(e){ try{ document.title = 'YTERR:' + e.data; }catch(_){ } }
                    }
                  });
                }
                function __mute(){ try{ if(player) player.mute(); }catch(e){} }
                function __unmute(){ try{ if(player) player.unMute(); }catch(e){} }
              </script>
            </body></html>
        """.formatted(vid, vid);
    }

    /** 패널/미니바 보이기 토글 */
    private void showPanel(boolean show) {
        if (musicBar != null) {
            musicBar.setManaged(show);
            musicBar.setVisible(show);
        }
        if (musicMini != null) {
            musicMini.setManaged(!show);
            musicMini.setVisible(!show);
        }
    }
    private void showMini(boolean showMini) { showPanel(!showMini); }

    /** 패널의 정지 버튼(기존) → 음소거 토글로 사용 */
    @FXML private void onMusicStop()       { toggleMute(); }
    /** 미니바의 음소거 버튼 */
    @FXML private void onMusicMuteToggle() { toggleMute(); }

    private void toggleMute() {
        isMuted = !isMuted;
        syncMuteButton();
        applyMuteJS();
        applyMuteJSOnMini();
    }

    /** JS로 WebView 플레이어 음소거/해제 */
    private void applyMuteJS() {
        if (musicWeb == null) return;
        try {
            musicWeb.getEngine().executeScript(isMuted ? "__mute()" : "__unmute()");
        } catch (Exception ignored) {}
    }
    private void applyMuteJSOnMini(){
        if (miniWeb == null) return;
        try {
            miniWeb.getEngine().executeScript(isMuted ? "__mute()" : "__unmute()");
        } catch (Exception ignored) {}
    }

    /** 🔈/🔇 아이콘 동기화 */
    private void syncMuteButton() {
        if (musicMuteBtn != null) {
            musicMuteBtn.setText(isMuted ? "🔇" : "🔈");
        }
    }

    /** 패널만 숨기고(브금은 계속) 미니 아이콘 표시 */
    @FXML private void onMusicHide()       { showMini(true); }
    /** 미니바의 🎵 버튼 → 다시 펼치기 */
    @FXML private void onMusicMiniToggle() { showMini(false); }
    /** 유튜브로 열기 */
    @FXML private void onMusicOpenInYT()   { safeBrowse(currentVideoUrl); }

    /* ===================== 유틸: URL → videoId 파싱 ===================== */
    private String extractVideoId(String url) {
        if (url == null) return null;
        try {
            if (url.contains("youtu.be/")) {
                String id = url.substring(url.indexOf("youtu.be/") + 9);
                int q = id.indexOf('?');
                return (q >= 0) ? id.substring(0, q) : id;
            }
            if (url.contains("watch?v=")) {
                String qs = url.substring(url.indexOf('?') + 1);
                for (String kv : qs.split("&")) {
                    String[] p = kv.split("=", 2);
                    if (p.length == 2 && p[0].equals("v"))
                        return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /* ===================== 저장된 BGM URL 로드(뷰어용) ===================== */
    private String resolveSavedMusicUrl(Long entryId) {
        if (entryId == null) return null;
        try (var con = com.share.dairy.util.DBConnection.getConnection()) {
            var dao = new com.share.dairy.dao.diary.DiaryAttachmentDao();
            var list = dao.findByEntryId(con, entryId); // DAO에 추가한 메서드 사용
            if (list != null) {
                for (var att : list) {
                    String u = att.getPathOrUrl();
                    if (u != null && (u.contains("youtube.com") || u.contains("youtu.be")))
                        return u;
                }
            }
        } catch (Exception ex) {
            System.err.println("[BGM] URL 로드 실패(entryId=" + entryId + "): " + ex.getMessage());
        }
        return null;
    }

    // [추가] WebView 생성 시도. 실패하면 null 반환해서 안전하게 폴백
    private WebView safeNewWebView() {
    try {
        WebView w = new WebView();
        w.setContextMenuEnabled(false);
        return w;
    } catch (Throwable t) {              // Error 포함 전부 캐치
        System.err.println("[WebView] create failed → fallback to external: " + t);
        return null;
    }
    }

}
