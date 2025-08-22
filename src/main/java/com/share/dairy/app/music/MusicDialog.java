package com.share.dairy.app.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

public class MusicDialog {

    private static final String YT_BASE = "http://localhost:8090";
    private final Consumer<MusicItem> onPick;

    public MusicDialog(Consumer<MusicItem> onPick) {
        this.onPick = onPick;
    }

    // 역전 응답 무시용 토큰(가장 최근 요청만 반영)
    private volatile long searchSeq = 0;

    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("MUSIC");

        TextField search = new TextField();
        search.setPromptText("노래/아티스트 검색");
        Button btn = new Button("검색");

        ListView<MusicItem> list = new ListView<>();
        list.setPlaceholder(new Label("검색 결과가 없어요"));

        // 보기 좋은 셀
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(MusicItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setText(null); return; }
                setText(it.title() + (it.channel() != null ? "  —  " + it.channel() : ""));
            }
        });

        // 우측 미리보기(자동재생 없음)
        WebView preview = new WebView();
        Hyperlink openInYoutube = new Hyperlink("YouTube에서 열기");
        openInYoutube.setVisible(false);
        openInYoutube.setOnAction(e -> {
            MusicItem sel = list.getSelectionModel().getSelectedItem();
            if (sel != null && sel.url() != null && !sel.url().isBlank()) {
                try { Desktop.getDesktop().browse(URI.create(sel.url())); } catch (Exception ignored) {}
            }
        });

        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(22, 22);
        loading.setVisible(false);

        // 검색 트리거
        btn.setOnAction(e -> doSearch(search.getText(), list, loading));
        search.setOnAction(e -> btn.fire()); // Enter로 검색

        // 선택 시: 컨트롤러로 콜백 + 우측 미리보기 로드
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (onPick != null) onPick.accept(n);  // 컨트롤러가 하단 패널에서 재생
            preview.getEngine().load("https://www.youtube.com/watch?v=" + n.videoId()); // 미리보기
            openInYoutube.setVisible(n.url() != null && !n.url().isBlank());
        });

        HBox top = new HBox(8, search, btn, loading);
        VBox right = new VBox(6, preview, openInYoutube);
        VBox.setVgrow(preview, Priority.ALWAYS);

        SplitPane split = new SplitPane(new StackPane(list), right);
        split.setDividerPositions(0.35);

        VBox root = new VBox(10, top, split);
        root.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    private void doSearch(String q, ListView<MusicItem> list, ProgressIndicator loading) {
        if (q == null || q.isBlank()) return;

        final long mySeq = ++searchSeq;
        loading.setVisible(true);

        try {
            // yt-server가 기본 max=8이지만 명시해도 무방
            String url = YT_BASE + "/api/yt/search?q=" +
                    URLEncoder.encode(q, StandardCharsets.UTF_8) + "&max=8";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                  .thenApply(res -> {
                      if (res.statusCode() != 200) {
                          throw new RuntimeException("HTTP " + res.statusCode());
                      }
                      return res.body();
                  })
                  .thenAccept(body -> {
                      // 이전 검색 결과가 더 늦게 도착했을 때 무시
                      if (mySeq != searchSeq) return;

                      try {
                          var om = new ObjectMapper();
                          Result[] arr = om.readValue(body, Result[].class);

                          MusicItem[] itemsArray = Arrays.stream(arr == null ? new Result[0] : arr)
                                  .map(r -> new MusicItem(
                                          r.videoId,
                                          unescapeHtml(r.title),
                                          r.channel,
                                          r.url))
                                  .toArray(MusicItem[]::new);

                          ObservableList<MusicItem> items = FXCollections.observableArrayList(itemsArray);
                          Platform.runLater(() -> {
                              list.setItems(items);
                              loading.setVisible(false);
                          });
                      } catch (Exception ex) {
                          ex.printStackTrace();
                          Platform.runLater(() -> {
                              list.setItems(FXCollections.observableArrayList());
                              loading.setVisible(false);
                              list.setPlaceholder(new Label("결과 파싱 실패"));
                          });
                      }
                  })
                  .exceptionally(ex -> {
                      ex.printStackTrace();
                      if (mySeq == searchSeq) {
                          Platform.runLater(() -> {
                              list.setItems(FXCollections.observableArrayList());
                              loading.setVisible(false);
                              list.setPlaceholder(new Label("서버 요청 실패"));
                          });
                      }
                      return null;
                  });

        } catch (Exception ex) {
            ex.printStackTrace();
            loading.setVisible(false);
        }
    }

    private static String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    // 서버 응답 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        public String videoId;
        public String title;
        public String channel;
        public String thumbnailUrl;
        public String url; // https://youtu.be/...
    }

    /** 컨트롤러에서 그대로 쓰는 모델(게터 이름 유지) */
    public static class MusicItem {
        private final String videoId;
        private final String title;
        private final String channel;
        private final String url;

        public MusicItem(String videoId, String title, String channel, String url) {
            this.videoId = videoId;
            this.title   = title;
            this.channel = channel;
            this.url     = url;
        }

        public String videoId() { return videoId; }
        public String title()   { return title; }
        public String channel() { return channel; }
        public String url()     { return url; }

        @Override public String toString() {
            return title + (channel != null ? "  —  " + channel : "");
        }
    }
}
