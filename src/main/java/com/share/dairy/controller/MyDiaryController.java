package com.share.dairy.controller;

import com.share.dairy.app.music.MusicDialog;
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.share.dairy.util.game.AvoidRocksPane;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.scene.layout.Region;
import javafx.scene.Node;


/**
 * MyDiaryController (옵션 B 적용본)
 * - 카드에 "보기" 버튼만 추가 (전역 필터/투명 버튼 없음)
 * - 다른 기능들은 변경 없음
 */
public class MyDiaryController {

    /* ========== [폼/목록 공통] ========== */
    @FXML private TextField titleField, placeField, musicField, timeField;
    @FXML private TextArea  contentArea;
    @FXML private VBox      listContainer;

    /* ========== [상단 MUSIC 버튼] ========== */
    @FXML private Button btnMusic;

    /* ========== [음악 패널/미니바] ========== */
    @FXML private HBox     musicBar;         // 큰 패널
    @FXML private WebView  musicWeb;
    @FXML private Label    musicTitle, musicChannel;
    @FXML private Hyperlink musicOpenLink;

    @FXML private HBox     musicMini;        // 접었을 때 미니 아이콘
    @FXML private Button   musicMiniToggle;  // 펼치기 버튼
    @FXML private Button   musicMuteBtn;     // 🔈/🔇

    /* ========== [우하단 연필 FAB — 목록에서만 노출] ========== */
    @FXML private Button pencilFab;

    /* ========== [상태/서비스] ========== */
    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /* 저장 후 후처리(목록 갱신 등) */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    /* 새 일기 모달 모드 & 저장 콜백 */
    private boolean        dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) { this.dialogMode = dialogMode; if (dialogMode) forceHideFab(); }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    /* ========== [오버레이/폴링 공통] ========== */
    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService poller;
    private Stage loadingStage;
    private ProgressBar overlayProgress;
    private Label overlayPercent, overlayMsg;
    private AvoidRocksPane gamePane;

    /* 상태 API 없을 때 테스트용 */
    private static final boolean FAKE_STATUS_MODE = false;
    private ScheduledFuture<?> fakeFuture;
    private int fakeProgress = 0;

    /* ========== [음악 패널 상태] ========== */
    private String  currentVideoId, currentVideoUrl;
    private boolean minimizeOnReady = false;
    private boolean playerReady     = false;
    private boolean isMuted         = false;

    /* ======================================
     *               초기화
     * ====================================== */
    @FXML
    public void initialize() {
        if (titleField  != null) titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
        if (listContainer != null){
            listContainer.setMouseTransparent(false);
            listContainer.setPickOnBounds(true);
        }
        refreshList();

        if (btnMusic != null) btnMusic.setOnAction(e -> openMusicDialog());

        // 음악 패널/미니바 기본 비노출 (숨길 때는 클릭 통과)
        if (musicBar != null) {
            musicBar.setVisible(false);
            musicBar.setManaged(false);
            musicBar.setMouseTransparent(true);
        }
        if (musicMini != null) {
            musicMini.setVisible(false);
            musicMini.setManaged(false);
            musicMini.setMouseTransparent(true);
        }

        // WebView UA 최신화(임베드 신뢰도 ↑)
        if (musicWeb != null) {
            musicWeb.getEngine().setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            );
        }

        // 모달이면 FAB 안전 숨김
        Platform.runLater(() -> { if (dialogMode) forceHideFab(); });

        // 음소거 버튼 초기 아이콘
        syncMuteButton();

        // FAB 레이어가 클릭 가리는 문제 방지 (FAB만 클릭되게)
        Platform.runLater(() -> {
            if (pencilFab != null) {
                javafx.scene.Parent p = pencilFab.getParent();
                while (p != null && !(p instanceof javafx.scene.layout.Pane)) p = p.getParent();
                if (p instanceof javafx.scene.layout.Pane fabLayer) {
                    fabLayer.setPickOnBounds(false);
                    fabLayer.setMouseTransparent(false);
                }
                pencilFab.setPickOnBounds(true);
            }
        });

        // 옵션 B: 전역 클릭 필터/투명 버튼 설치 안 함
    }

    /* ======================================
     *               상단 버튼
     * ====================================== */
    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }
    @FXML private void onMusic(){ openMusicDialog(); }
    @FXML private void onTime(){  if (timeField  != null) timeField.requestFocus(); }

    @FXML
    private void onEdit() {
        if (titleField  != null) titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
    }

    /* ======================================
     *      저장 → 분석 → 이미지 생성 트리거
     * ====================================== */
    @FXML
    private void onSave() {
        try {
            Long uid = com.share.dairy.auth.UserSession.currentId();
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
            long entryId = new DiaryEntryDao().save(entry);

            // 백그라운드로 분석 → 이미지 생성 트리거 → 오버레이+폴링
            new Thread(() -> {
                try {
                    // 1) GPT 분석
                    new DiaryAnalysisService().process(entryId);

                    // 2) 안내
                    Platform.runLater(() ->
                        new Alert(Alert.AlertType.INFORMATION,
                                  "분석 완료! 키워드/캐릭터 이미지 생성을 시작합니다.").show()
                    );

                    // 3) 이미지 생성 트리거
                    triggerAutoImage(entryId);

                    // 4) 오버레이 + 상태 폴링 시작
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

    /* ======================================
     *             FAB → 새 일기 모달
     * ====================================== */
    @FXML
    private void onClickFabPencil() {
        try {
            FXMLLoader fxml = new FXMLLoader(
                    getClass().getResource("/fxml/diary/my_diary/my-diary-view.fxml")); // ← 고정 경로
            Parent root = fxml.load();

            MyDiaryController child = fxml.getController();
            child.setDialogMode(true);
            child.setOnSaved(id -> refreshList());
            child.forceHideFab(); // 안전빵

            // 혹시라도 lookup으로 한 번 더 제거
            var fab = root.lookup("#pencilFab");
            if (fab == null) fab = root.lookup(".fab");
            if (fab != null) { fab.setVisible(false); fab.setManaged(false); }

            Stage dlg = new Stage();
            if (listContainer != null && listContainer.getScene() != null) {
                dlg.initOwner(listContainer.getScene().getWindow());
            }
            dlg.initModality(Modality.APPLICATION_MODAL);
            dlg.setTitle("New Diary");
            dlg.setScene(new Scene(root));
            dlg.showAndWait();

            refreshList();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                "새 일기 화면을 열 수 없습니다.\n" + (e.getMessage() == null ? e.toString() : e.getMessage())
            ).showAndWait();
        }
    }

    /* ======================================
     *                목록 렌더
     * ====================================== */
    private void refreshList() {
        if (listContainer == null) return;

        Long uid = com.share.dairy.auth.UserSession.currentId();
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

    // 카드 하나 생성: 내용 + 우측 아래 "열기" 링크
    private javafx.scene.Node makeCard(com.share.dairy.model.diary.DiaryEntry d) {
    VBox card = new VBox(6);
    card.getStyleClass().add("diary-card");

    Label dateLbl  = new Label("DATE "    + java.util.Optional.ofNullable(d.getEntryDate()).orElse(null));
    Label titleLbl = new Label("TITLE "   + java.util.Optional.ofNullable(d.getTitle()).orElse(""));
    Label bodyLbl  = new Label("CONTENTS "+ java.util.Optional.ofNullable(d.getDiaryContent()).orElse(""));
    card.getChildren().addAll(dateLbl, titleLbl, bodyLbl);

    // ---- 여기부터 추가: 우하단 "열기" 링크 ----
    javafx.scene.layout.HBox linkRow = new javafx.scene.layout.HBox(8);
    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
    javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

    Hyperlink openLink = new Hyperlink("열기");
    // 팀 CSS가 링크 색을 죽여버릴 수 있으니 강제로 보이게(필요 없으면 지워도 됨)
    openLink.setStyle("-fx-text-fill:#3366ff; -fx-font-weight:bold;");
    openLink.setFocusTraversable(false);
    openLink.setOnAction(ev -> {
        System.out.println("[MYDIARY] OPEN: " + d.getTitle() + " / " + d.getEntryDate());
        openDiaryViewer(d);
    });

    linkRow.getChildren().addAll(spacer, openLink);
    card.getChildren().add(linkRow);
    // ---- 추가 끝 ----

    return card;
    }

    /** 읽기 전용 모달 (MY DIARY 카드 → 보기 버튼) */
    private void openDiaryViewer(DiaryEntry d) {
        System.out.println("[MYDIARY] openDiaryViewer()");
        Stage dlg = new Stage();

        // 소유자 지정(있으면)
        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner(listContainer.getScene().getWindow());
        } else {
            Stage st = currentStage();
            if (st != null) dlg.initOwner(st);
        }
        dlg.initModality(Modality.WINDOW_MODAL);

        // 날짜 포맷: yyyy.MM.dd
        String dateDot = Optional.ofNullable(d.getEntryDate())
                .map(ld -> ld.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .orElse("");

        String titleText = Optional.ofNullable(d.getTitle())
                .map(String::trim).filter(s -> !s.isEmpty()).orElse("TITLE");

        Label lblTopDate = new Label(dateDot);
        lblTopDate.setStyle("-fx-font-size: 13; -fx-text-fill: #333;");

        Label lblTitle = new Label(titleText);
        lblTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        Label lblSubDate = new Label(dateDot);
        lblSubDate.setStyle("-fx-text-fill: #666;");

        TextArea body = new TextArea(Optional.ofNullable(d.getDiaryContent()).orElse(""));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);
        body.setStyle("-fx-font-size: 13;");

        Button close = new Button("닫기");
        close.setOnAction(ev -> dlg.close());

        VBox root = new VBox(10, lblTopDate, lblTitle, lblSubDate, body, close);
        root.setPadding(new Insets(16));

        dlg.setTitle(dateDot.isEmpty() ? "MY DIARY" : dateDot);
        dlg.setScene(new Scene(root, 720, 520));
        dlg.setResizable(false);

        // ESC로 닫기
        dlg.getScene().setOnKeyPressed(k -> {
            if (k.getCode() == javafx.scene.input.KeyCode.ESCAPE) dlg.close();
        });

        // 배경 살짝 어둡게(있을 때만)
        Stage owner = (Stage) (listContainer != null && listContainer.getScene() != null
                ? listContainer.getScene().getWindow() : currentStage());
        if (owner != null && owner.getScene() != null) owner.getScene().getRoot().setOpacity(0.60);
        try { dlg.showAndWait(); } finally {
            if (owner != null && owner.getScene() != null) owner.getScene().getRoot().setOpacity(1.0);
        }
    }

    private Stage currentStage() {
        if (titleField  != null && titleField.getScene()  != null) return (Stage) titleField.getScene().getWindow();
        if (contentArea != null && contentArea.getScene() != null) return (Stage) contentArea.getScene().getWindow();
        return null;
    }

    /* ======================================
     *     [음악 패널/미니바]
     * ====================================== */

    /** MUSIC 버튼 → 검색 모달 → 선택 시 브금 재생(성공 즉시 미니로 접기) */
    private void openMusicDialog() {
        try {
            new MusicDialog(item -> {
                if (item == null) return;
                String vid = item.videoId();
                if (vid == null || vid.isBlank()) return;
                playInPanel(vid, item.title(), item.channel(), item.url(), true);
            }).show();
        } catch (Throwable ex) {
            new Alert(Alert.AlertType.ERROR,
                "음악 검색창을 열 수 없습니다:\n" + (ex.getMessage() == null ? ex.toString() : ex.getMessage()))
                .showAndWait();
        }
    }

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

        // JS에서 임베드 금지(150/101) 감지용
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

    /** 패널 보이기/숨기기 */
    private void showPanel(boolean show) {
        if (musicBar != null) {
            musicBar.setManaged(show);
            musicBar.setVisible(show);
            musicBar.setMouseTransparent(!show);  // 숨기면 클릭 통과
        }
        if (musicMini != null) {
            musicMini.setManaged(!show);
            musicMini.setVisible(!show);
            musicMini.setMouseTransparent(show);  // 미니가 보일 땐 미니만 클릭
        }
    }
    private void showMini(boolean showMini) { showPanel(!showMini); }

    /** 패널 왼쪽 버튼(기존 정지) → 음소거 토글로 사용 */
    @FXML private void onMusicStop()       { toggleMute(); }
    /** 미니바의 음소거 버튼 */
    @FXML private void onMusicMuteToggle() { toggleMute(); }

    private void toggleMute() {
        isMuted = !isMuted;
        syncMuteButton();
        applyMuteJS();
    }

    private void applyMuteJS() {
        if (musicWeb == null) return;
        try {
            String js = isMuted ? "__mute()" : "__unmute()";
            musicWeb.getEngine().executeScript(js);
        } catch (Exception ignored) {}
    }

    /** 패널만 숨기고(브금은 계속) 미니 아이콘 표시 */
    @FXML private void onMusicHide()       { showMini(true); }
    /** 미니바의 🎵 버튼 → 다시 펼치기 */
    @FXML private void onMusicMiniToggle() { showMini(false); }
    /** 유튜브로 열기 */
    @FXML private void onMusicOpenInYT() {
        try {
            if (currentVideoUrl != null && !currentVideoUrl.isBlank()) {
                Desktop.getDesktop().browse(URI.create(currentVideoUrl));
            }
        } catch (Exception ignored) {}
    }

    /** FAB 강제 숨김 */
    public void forceHideFab() {
        if (pencilFab != null) {
            pencilFab.setVisible(false);
            pencilFab.setManaged(false);
        }
    }

    /** 🔈/🔇 아이콘 동기화 */
    private void syncMuteButton() {
        if (musicMuteBtn != null) {
            musicMuteBtn.setText(isMuted ? "🔇" : "🔈");
        }
    }

    /* ======================================
     *    이미지 자동 생성(서버 트리거)
     * ====================================== */
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

    /* ======================================
     *     상태 조회 + 오버레이(게임) + 폴링
     * ====================================== */
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
        // 이미 떠 있으면 재사용
        if (loadingStage != null && loadingStage.isShowing()) return;

        // 오버레이 UI
        Label title = new Label("키워드/캐릭터 이미지 생성 중...");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        overlayProgress = new ProgressBar(-1); // 진행률 미확정 → indeterminate
        overlayProgress.setPrefWidth(420);

        overlayPercent = new Label("0%");
        overlayPercent.setTextFill(Color.WHITE);
        overlayPercent.setStyle("-fx-font-weight: bold;");

        overlayMsg = new Label("잠시만 기다려 주세요.");
        overlayMsg.setTextFill(Color.rgb(230,230,230));
        overlayMsg.setStyle("-fx-opacity: 0.92;");

        HBox prog = new HBox(10, overlayProgress, overlayPercent);
        prog.setAlignment(Pos.CENTER);

        // 미니게임 삽입
        gamePane = new AvoidRocksPane(520, 280);

        Button closeBtn = new Button("오버레이 닫기"); // 취소 아님, UI만 닫기
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

        // 창 닫힐 때 정리
        loadingStage.setOnCloseRequest(ev -> {
            stopPolling();
            stopFakeProgress();
            if (gamePane != null) gamePane.stop();
        });

        loadingStage.show();
        gamePane.requestGameFocus();

        // 폴링 시작(또는 FAKE 모드)
        if (FAKE_STATUS_MODE) {
            startFakeProgress(entryId);
            return;
        }

        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            try {
                JsonNode st = fetchImageStatus(entryId);
                String status = st.path("status").asText("RUNNING");
                int    progress = st.path("progress").asInt(-1);
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

        // 진행률에 따라 게임 배경 틴트
        if (gamePane != null && progress >= 0) gamePane.setProgressTint(progress);
    }

    private void onImageDone(long entryId) {
        if (loadingStage != null) loadingStage.close();
        if (gamePane != null) gamePane.stop();

        new Alert(Alert.AlertType.INFORMATION,
            "일기 저장 및 분석/이미지 생성 완료!\nentry_id=" + entryId).showAndWait();

        if (onSaved    != null) onSaved.accept(entryId);
        if (afterSave  != null) afterSave.run();
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

    /* 테스트용 가짜 진행률 */
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
        poller = ex;
    }

    private void stopFakeProgress() {
        if (fakeFuture != null) {
            fakeFuture.cancel(true);
            fakeFuture = null;
        }
    }
}
