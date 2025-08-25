package com.share.dairy.controller;

import com.share.dairy.app.music.MusicDialog;
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

// ===== [추가] 진행률 상태 파싱용 Jackson =====
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// ===== [추가] JavaFX UI 구성/게임/오버레이 관련 =====
import com.share.dairy.util.game.AvoidRocksPane; // ← 별도 파일로 분리된 '돌 피하기' 게임 컴포넌트
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.paint.Color;
import javafx.scene.layout.HBox;     
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.sql.SQLException;   

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
    @FXML private TextArea  contentArea;

    /* 상단 MUSIC 버튼(있으면 핸들러 연결) */
    @FXML private Button btnMusic;

    /* 목록 컨테이너(목록 모드일 때만 존재) */
    @FXML private VBox listContainer;

    /* ▼ 하단 음악 패널 + 미니바 */
    @FXML private HBox    musicBar;         // 큰 패널
    @FXML private WebView musicWeb;
    @FXML private Label   musicTitle, musicChannel;
    @FXML private Hyperlink musicOpenLink;

    @FXML private HBox    musicMini;        // 접었을 때 보이는 미니 아이콘 바
    @FXML private Button  musicMiniToggle;  // 펼치기 버튼
    @FXML private Button  musicMuteBtn;     // 🔈/🔇 음소거 토글(있으면 사용)

    /* 우하단 연필 FAB (메인에서만 보여야 함) */
    @FXML private Button  pencilFab;

    private String currentVideoId, currentVideoUrl;

    // 미니아이콘 자동 전환/플레이어 상태
    private boolean minimizeOnReady = false;
    private boolean playerReady     = false;
    private boolean isMuted         = false;

    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    // ✅ 수정: 하드코딩 제거(=FK 오류 원인). 외부에서 로그인 유저 ID 주입받도록 함.
   

    // ===== 서버 URL/HTTP 클라이언트 =====
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /* 저장 후 후처리(목록 갱신 등) */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    /* 새 일기 모달 모드 & 저장 콜백(필요 시) */
    private boolean        dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) {
        this.dialogMode = dialogMode;
        if (dialogMode) forceHideFab();
    }
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
        if (titleField  != null) titleField.setDisable(false);
        if (contentArea != null) contentArea.setDisable(false);
        if (listContainer != null) refreshList();
        if (btnMusic != null) btnMusic.setOnAction(e -> openMusicDialog());

        // 음악 패널/미니바 기본 상태
        if (musicBar  != null) { musicBar.setVisible(false);  musicBar.setManaged(false); }
        if (musicMini != null) { musicMini.setVisible(false); musicMini.setManaged(false); }

        // 최신 브라우저 UA 지정(임베드 신뢰도 ↑)
        if (musicWeb != null) {
            musicWeb.getEngine().setUserAgent(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            );
        }

        // 모달이면 연필을 확실히 가린다(세이프가드)
        Platform.runLater(() -> { if (dialogMode) forceHideFab(); });

        // 음소거 버튼 초기 아이콘
        syncMuteButton();
    }

    /* ===== 버튼들 ===== */
    @FXML private void onPlace(){ if (placeField != null) placeField.requestFocus(); }
    @FXML private void onMusic(){ openMusicDialog(); }
    @FXML private void onTime(){  if (timeField  != null) timeField.requestFocus(); }

    @FXML
    private void onEdit() {
        if (titleField  != null) titleField.setDisable(false);
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
            DiaryEntryDao dao = new DiaryEntryDao();
            long entryId = dao.save(entry);

            // 분석/이미지 생성 트리거는 백그라운드로
            // 분석/이미지 생성 트리거는 백그라운드로
            new Thread(() -> {
                try {
                    // 1) GPT 분석
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

        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 연필(FAB) → 새 일기 모달 (원래 쓰던 FXML 그대로) */
    @FXML
    private void onClickFabPencil() throws IOException {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/fxml/diary/my_diary/my_diary.fxml"));
        Parent root = fxml.load();

        MyDiaryController child = fxml.getController();
        child.setDialogMode(true);
        child.setOnSaved(id -> refreshList());

        // 안전빵으로 즉시 강제 숨김
        child.forceHideFab();
        // 혹시라도 lookup으로 한 번 더 제거
        javafx.scene.Node fab = root.lookup("#pencilFab");
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
    }

    /* ===== 목록 렌더 ===== */
    private void refreshList() {
    if (listContainer == null) return;

    Long uid = com.share.dairy.auth.UserSession.currentId();
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

        Label date  = new Label(dateText);
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
        if (titleField  != null && titleField.getScene()  != null) return (Stage) titleField.getScene().getWindow();
        if (contentArea != null && contentArea.getScene() != null) return (Stage) contentArea.getScene().getWindow();
        return null;
    }

    /* ====== 음악 검색/재생 연동 ====== */

    /** MUSIC 버튼 → 검색 모달 열고, 선택한 곡을 브금으로 재생(자동 미니모드) */
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

    /**
     * 하단 패널 재생(브금처럼 반복).
     * autoMinimize=true면 로드 성공 직후 미니로 접음.
     * 임베드 금지(오류 150/101)도 감지해서 자동 미니모드 + 유튜브 링크만 남김.
     */
    private void playInPanel(String videoId, String title, String channel, String url, boolean autoMinimize) {
        if (musicWeb == null || musicBar == null) return;

        currentVideoId  = videoId;
        currentVideoUrl = url;

        if (musicTitle   != null) musicTitle.setText(title   == null ? "" : title);
        if (musicChannel != null) musicChannel.setText(channel == null ? "" : channel);
        if (musicOpenLink!= null) musicOpenLink.setVisible(url != null && !url.isBlank());

        String html = playerHtml(videoId);
        WebEngine eng = musicWeb.getEngine();

        playerReady = false;
        // 새 재생 시 기본 '음소거 해제' 상태로 시작
        isMuted     = false;
        minimizeOnReady = autoMinimize;
        syncMuteButton();               // 아이콘 초기화

        // 로드 성공/실패 감시
        eng.getLoadWorker().stateProperty().addListener((obs, old, st) -> {
            if (st == Worker.State.SUCCEEDED) {
                playerReady = true;
                if (minimizeOnReady) {
                    minimizeOnReady = false;
                    showMini(true);
                }
                // 준비된 시점의 원하는 음소거 상태 반영
                applyMuteJS();
            } else if (st == Worker.State.FAILED) {
                eng.load("https://www.youtube.com/watch?v=" + videoId);
                showMini(true);
                applyMuteJS();
            }
        });

        // JS에서 임베드 금지 에러 신호(150/101) 전달 → 폴백 + 미니
        eng.titleProperty().addListener((o, ov, nv) -> {
            if (nv != null && nv.startsWith("YTERR:")) {
                eng.load("https://www.youtube.com/watch?v=" + videoId);
                showMini(true);
                applyMuteJS();
            }
        });

        eng.loadContent(html, "text/html");

        // 우선은 펼친 상태에서 로드(성공하면 자동 미니)
        showPanel(true);
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
        musicBar.setManaged(show); musicBar.setVisible(show);
        if (musicMini != null) { musicMini.setManaged(!show); musicMini.setVisible(!show); }
    }
    private void showMini(boolean showMini) { showPanel(!showMini); }

    /* ===== 여기부터 음소거 토글 공통 처리 ===== */

    /** 패널 왼쪽 버튼(기존 정지 버튼)도 음소거 토글로 사용 */
    @FXML private void onMusicStop() {
        toggleMute(); // <- 정지 대신 음소거 토글로 동작하게 변경
    }

    /** 미니바의 음소거 버튼(있다면) */
    @FXML private void onMusicMuteToggle() {
        toggleMute();
    }

    /** 실제 토글 로직(버튼 공통) */
    private void toggleMute() {
        isMuted = !isMuted;
        syncMuteButton();  // 🔈/🔇 즉시 반영
        applyMuteJS();     // WebView에 실제 음소거 적용(준비 전이어도 안전)
    }

    /** 현재 isMuted 상태를 WebView 플레이어에 반영 */
    private void applyMuteJS() {
        if (musicWeb == null) return;
        try {
            WebEngine eng = musicWeb.getEngine();
            String js = isMuted ? "__mute()" : "__unmute()";
            eng.executeScript(js);
        } catch (Exception ignored) {}
    }

    /** 패널만 숨기고(브금은 계속) 미니 아이콘 표시 */
    @FXML private void onMusicHide() { showMini(true); }

    /** 미니바의 🎵 버튼 → 다시 펼치기 */
    @FXML private void onMusicMiniToggle() { showMini(false); }

    @FXML private void onMusicOpenInYT() {
        try {
            if (currentVideoUrl != null && !currentVideoUrl.isBlank()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(currentVideoUrl));
            }
        } catch (Exception ignored) {}
    }

    /* --- 연필 FAB 강제 숨김 헬퍼 --- */
    public void forceHideFab() {
        if (pencilFab != null) {
            pencilFab.setVisible(false);
            pencilFab.setManaged(false);
        }
    }

    /* --- 🔈/🔇 아이콘 동기화 --- */
    private void syncMuteButton() {
        if (musicMuteBtn != null) {
            musicMuteBtn.setText(isMuted ? "🔇" : "🔈");
        }
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
        // 패널 왼쪽 버튼 아이콘도 같이 쓰고 싶으면 FXML에서 동일 버튼을 musicMuteBtn로 매핑하면 됨.
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
    
