package com.share.dairy.controller;

import com.share.dairy.app.music.MusicDialog;
import com.share.dairy.dao.diary.DiaryAttachmentDao;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.service.diary.DiaryWriteService;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static com.share.dairy.auth.UserSession.currentId;

public class HubListController {

    @FXML private VBox listContainer;

    private final DiaryWriteService service = new DiaryWriteService();
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML
    private void initialize() {
        if (listContainer != null) {
            listContainer.setSpacing(16);
            listContainer.setPadding(new Insets(18));
            if (listContainer.getParent() instanceof ScrollPane sp) sp.setFitToWidth(true);
        }
        refreshList();
    }

    /** DB → 카드 렌더 (MY DIARY: shared_diary_id IS NULL 만 가져오도록 서비스가 필터링) */
    public void refreshList() {
        List<DiaryEntry> rows;
        try {
            rows = service.loadMyDiaryList(currentId());
        } catch (RuntimeException ex) {
            listContainer.getChildren().setAll(new Label("목록 조회 실패: " + ex.getMessage()));
            return;
        }

        listContainer.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("작성된 일기가 없습니다.");
            empty.setStyle("-fx-text-fill:#666; -fx-font-size:13;");
            listContainer.getChildren().add(empty);
            return;
        }

        rows.forEach(d -> listContainer.getChildren().add(makeCard(d)));
    }

    /** 카드 UI + 클릭 시 뷰어 모달 */
    private VBox makeCard(DiaryEntry d) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(card, new Insets(0, 0, 8, 0));
        card.setStyle(
                "-fx-background-color:white;" +
                        "-fx-background-radius:12;" +
                        "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.10), 10, 0, 0, 4);"
        );
        card.setCursor(Cursor.HAND);

        String dateStr = (d.getEntryDate() == null) ? "" : d.getEntryDate().format(DF);
        Label date = new Label("DATE " + dateStr);
        date.setStyle("-fx-text-fill:#666; -fx-font-size:12; -fx-font-weight:bold;");

        String titleStr = Optional.ofNullable(d.getTitle()).map(String::trim)
                .filter(s -> !s.isEmpty()).orElse("(제목 없음)");
        Label title = new Label("TITLE " + titleStr);
        title.setStyle("-fx-text-fill:#2d2150; -fx-font-size:15; -fx-font-weight:700;");

        String body = Optional.ofNullable(d.getDiaryContent()).orElse("");
        if (body.length() > 240) body = body.substring(0, 240) + "…";
        Label content = new Label("CONTENTS " + body);
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#333; -fx-font-size:13;");

        card.getChildren().addAll(date, title, content);

        // 클릭(좌클릭) → 모달 뷰어
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) openViewer(d);
        });

        // Hover 느낌
        card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle() + "-fx-background-color:#fff7fd;"));
        card.setOnMouseExited(e ->
                card.setStyle(card.getStyle().replace("-fx-background-color:#fff7fd;", "")));

        return card;
    }

    /** 읽기 전용 모달 (+ BGM 미니바: WebView 실패 시 외부 브라우저 폴백) */
    private void openViewer(DiaryEntry d) {
        Stage dlg = new Stage();
        if (listContainer != null && listContainer.getScene() != null) {
            dlg.initOwner((Stage) listContainer.getScene().getWindow());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("MY DIARY");

        Label date = new Label("DATE " + (d.getEntryDate() == null ? "" : d.getEntryDate().format(DF)));
        date.setStyle("-fx-text-fill:#666; -fx-font-size:12;");

        Label title = new Label(Optional.ofNullable(d.getTitle()).filter(s -> !s.isBlank()).orElse("(제목 없음)"));
        title.setStyle("-fx-font-size:17; -fx-font-weight:800;");

        TextArea body = new TextArea(Optional.ofNullable(d.getDiaryContent()).orElse(""));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);

        // ----- BGM 미니바 -----
        var optVw = tryCreateWebView();  // 안전 생성 (실패 시 empty)
        final boolean[] isMuted = { false };
        final String[] currentUrl = { null };

        Button muteBtn = new Button("🔈");
        Label  playing = new Label("♪  BGM 재생중");
        playing.setStyle("-fx-font-weight:bold;");

        Hyperlink openLink = new Hyperlink("유튜브로 열기");
        openLink.setDisable(true);
        openLink.setOnAction(ev -> openExternal(currentUrl[0]));

        Button pickBtn = new Button("MUSIC");

        Region spacer  = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bgmBar = new HBox(12, muteBtn, playing, spacer, openLink, pickBtn);
        bgmBar.setPadding(new Insets(8, 10, 8, 10));
        bgmBar.setStyle("-fx-background-color:#f7e7f7; -fx-background-radius:10;");

        if (optVw.isPresent()) {
            // ===== 정상 경로: WebView가 성공적으로 생성됨 → 내장 재생 =====
            WebView vw = optVw.get();
            vw.setPrefSize(1, 1); vw.setMinSize(1, 1); vw.setMaxSize(1, 1);
            vw.setOpacity(0); vw.setMouseTransparent(true);

            var eng = vw.getEngine();
            eng.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");

            java.util.function.BiConsumer<String, String> loadById = (vid, url) -> {
                if (vid == null || vid.isBlank()) return;
                currentUrl[0] = url;
                if (currentUrl[0] != null && !currentUrl[0].isBlank()) openLink.setDisable(false);

                String html = viewerPlayerHtml(vid);
                eng.getLoadWorker().stateProperty().addListener((obs, old, st) -> {
                    if (st == javafx.concurrent.Worker.State.SUCCEEDED) {
                        try {
                            eng.executeScript("try{player.playVideo();}catch(e){}");
                            if (isMuted[0]) eng.executeScript("__mute()");
                        } catch (Exception ignored) {}
                    }
                });
                eng.loadContent(html, "text/html");
            };

            // DB 저장 URL 자동 로드
            String savedUrl = loadFirstAttachmentUrl(d);
            if (savedUrl != null && !savedUrl.isBlank()) {
                String vid = extractVideoId(savedUrl);
                if (vid != null) loadById.accept(vid, savedUrl);
            }

            // MUSIC 버튼 → 선택한 곡 재생
            pickBtn.setOnAction(e -> new MusicDialog(item -> {
                if (item == null || item.videoId() == null || item.videoId().isBlank()) return;
                String url = (item.url() != null && !item.url().isBlank())
                        ? item.url()
                        : ("https://www.youtube.com/watch?v=" + item.videoId());
                loadById.accept(item.videoId(), url);
            }).show());

            // 음소거
            muteBtn.setOnAction(e -> {
                isMuted[0] = !isMuted[0];
                muteBtn.setText(isMuted[0] ? "🔇" : "🔈");
                try { eng.executeScript(isMuted[0] ? "__mute()" : "__unmute()"); } catch (Exception ignored) {}
            });

            Button close = new Button("닫기");
            close.setOnAction(ev -> dlg.close());

            VBox root = new VBox(10, date, title, body, bgmBar, vw, close);
            root.setPadding(new Insets(16));
            dlg.setScene(new Scene(root, 640, 480));
            dlg.showAndWait();

        } else {
            // ===== 폴백 경로: WebView 생성 실패 → 외부 브라우저만 사용 =====
            playing.setText("♪  외부 브라우저 재생");
            muteBtn.setDisable(true);

            // DB 저장 URL 있으면 링크 활성화
            String savedUrl = loadFirstAttachmentUrl(d);
            if (savedUrl != null && !savedUrl.isBlank()) {
                currentUrl[0] = savedUrl;
                openLink.setDisable(false);
            }

            // MUSIC → 외부 브라우저 열기
            pickBtn.setOnAction(e -> new MusicDialog(item -> {
                if (item == null || (item.videoId() == null && (item.url() == null || item.url().isBlank()))) return;
                String url = (item.url() != null && !item.url().isBlank())
                        ? item.url()
                        : ("https://www.youtube.com/watch?v=" + item.videoId());
                currentUrl[0] = url;
                openLink.setDisable(false);
                openExternal(url);
            }).show());

            Button close = new Button("닫기");
            close.setOnAction(ev -> dlg.close());

            VBox root = new VBox(10, date, title, body, bgmBar, close);
            root.setPadding(new Insets(16));
            dlg.setScene(new Scene(root, 640, 480));
            dlg.showAndWait();
        }
    }

    /** 모달용 간단 임베드 HTML */
    private String viewerPlayerHtml(String videoId) {
        String vid = (videoId == null) ? "" : videoId;
        return """
            <!doctype html><html><head><meta charset="utf-8"></head>
            <body style="margin:0;background:#000">
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player;
                function onYouTubeIframeAPIReady(){
                  player = new YT.Player('player', {
                    height:'1', width:'1',
                    videoId:'%s',
                    playerVars:{ 'autoplay':1, 'rel':0, 'modestbranding':1, 'playsinline':1, 'loop':1, 'playlist':'%s', 'enablejsapi':1 },
                    events:{
                      'onReady': function(e){ try{ e.target.playVideo(); }catch(_){} },
                      'onError': function(e){ /* 임베드 금지 등은 무시 */ }
                    }
                  });
                }
                function __mute(){ try{ if(player) player.mute(); }catch(e){} }
                function __unmute(){ try{ if(player) player.unMute(); }catch(e){} }
              </script>
            </body></html>
        """.formatted(vid, vid);
    }

    // DiaryEntry의 PK getter가 프로젝트마다 다를 수 있어 둘 다 시도
    private long entryPk(DiaryEntry d) {
        try { return (Long) d.getClass().getMethod("getEntryId").invoke(d); } catch (Exception ignore) {}
        try { return (Long) d.getClass().getMethod("getDiaryId").invoke(d); } catch (Exception ignore) {}
        throw new IllegalStateException("DiaryEntry PK getter(getEntryId/getDiaryId) 확인 필요");
    }

    // DB에서 첫 번째 첨부 URL 하나 가져오기
    private String loadFirstAttachmentUrl(DiaryEntry d) {
        try {
            long entryId = entryPk(d);
            var list = new DiaryAttachmentDao().findByEntry(entryId);
            for (DiaryAttachment a : list) {
                String u = a.getPathOrUrl();
                if (u != null && !u.isBlank()) return u;
            }
        } catch (Exception ex) {
            System.err.println("BGM 로드 실패: " + ex.getMessage());
        }
        return null;
    }

    // 유튜브 URL → videoId 추출 (watch?v= / youtu.be / embed / shorts 지원)
    private String extractVideoId(String url) {
        if (url == null) return null;
        // 1) watch?v=ID
        int idx = url.indexOf("v=");
        if (idx >= 0) {
            String tail = url.substring(idx + 2);
            int cut = tail.indexOf('&');
            return (cut >= 0) ? tail.substring(0, cut) : tail;
        }
        // 2) youtu.be/ID
        String yb = "youtu.be/";
        idx = url.indexOf(yb);
        if (idx >= 0) {
            String tail = url.substring(idx + yb.length());
            int cut = tail.indexOf('?');
            return (cut >= 0) ? tail.substring(0, cut) : tail;
        }
        // 3) /embed/ID
        String emb = "/embed/";
        idx = url.indexOf(emb);
        if (idx >= 0) {
            String tail = url.substring(idx + emb.length());
            int cut = tail.indexOf('?');
            return (cut >= 0) ? tail.substring(0, cut) : tail;
        }
        // 4) /shorts/ID
        String sh = "/shorts/";
        idx = url.indexOf(sh);
        if (idx >= 0) {
            String tail = url.substring(idx + sh.length());
            int cut = tail.indexOf('?');
            return (cut >= 0) ? tail.substring(0, cut) : tail;
        }
        return null;
    }

    // ======= 안전 헬퍼들 =======

    /** WebView 안전 생성: 내부 모듈 접근 오류(IllegalAccessError 등) 시 empty 반환 */
    private Optional<WebView> tryCreateWebView() {
        try {
            return Optional.of(new WebView());
        } catch (Throwable t) { // Error/Exception 모두 잡음
            System.err.println("[WebView] create failed → fallback to external: " + t);
            return Optional.empty();
        }
    }

    /** OS별 외부 브라우저 열기 (여러 단계 폴백) */
    private void openExternal(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignore) { }
        try { new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start(); return; } catch (Exception ignore) { }
        try { new ProcessBuilder("cmd", "/c", "start", "", url).start(); return; } catch (Exception ignore) { }
        System.err.println("[ExternalOpen] failed: " + url);
    }
}