package com.share.dairy.controller;

import com.share.dairy.app.music.MusicDialog;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryWriteService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
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
    private final Long currentUserId = 1L; // 로그인 붙기 전 임시

    /* 새 일기 모달 모드 & 저장 콜백(필요 시) */
    private boolean        dialogMode = false;
    private Consumer<Long> onSaved;
    public void setDialogMode(boolean dialogMode) {
        this.dialogMode = dialogMode;
        if (dialogMode) forceHideFab();
    }
    public void setOnSaved(Consumer<Long> onSaved) { this.onSaved = onSaved; }

    /* 저장 후 후처리(메인 화면에서만 사용) */
    private Runnable afterSave;
    public void setAfterSave(Runnable r) { this.afterSave = r; }

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

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

            if (dialogMode) {
                if (onSaved != null) onSaved.accept(newId);
                Stage st = currentStage();
                if (st != null) st.close();
                return;
            }

            if (afterSave != null) afterSave.run();
            if (listContainer != null) refreshList();
            if (titleField  != null) titleField.clear();
            if (contentArea != null) contentArea.clear();

        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 연필(FAB) → 새 일기 모달 (원래 쓰던 FXML 그대로) */
    @FXML
    private void onClickFabPencil() throws IOException {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource(
                "/fxml/diary/my_diary/my-diary-view.fxml"
        ));
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
        listContainer.getChildren().clear();

        List<DiaryEntry> rows;
        try {
            rows = diaryWriteService.loadMyDiaryList(currentUserId);
        } catch (RuntimeException ex) {
            listContainer.getChildren().add(new Label("목록 조회 실패: " + deepestMessage(ex)));
            return;
        }

        for (DiaryEntry d : rows) listContainer.getChildren().add(makeCard(d));
    }

    private static String deepestMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return (c.getMessage() != null ? c.getMessage() : t.getMessage());
    }

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

    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(6);
        card.getStyleClass().add("diary-card");

        String dateText = "DATE " + (d.getEntryDate() == null ? "" : DAY_FMT.format(d.getEntryDate()));
        Label date = new Label(dateText);
        Label title = new Label(guessTitle(d));
        Label content = new Label("CONTENTS " + preview(d.getDiaryContent()));

        card.getChildren().addAll(date, title, content);

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

        Label date  = new Label("DATE " + (d.getEntryDate() == null ? "" : DAY_FMT.format(d.getEntryDate())));
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
        // 패널 왼쪽 버튼 아이콘도 같이 쓰고 싶으면 FXML에서 동일 버튼을 musicMuteBtn로 매핑하면 됨.
    }
}
