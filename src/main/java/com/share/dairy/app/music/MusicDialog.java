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
import java.util.Arrays;

public class MusicDialog {

    private static final String YT_BASE = "http://localhost:8090";

    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("MUSIC");

        TextField search = new TextField();
        search.setPromptText("노래/아티스트 검색");
        Button btn = new Button("검색");

        ListView<MusicItem> list = new ListView<>();
        list.setPlaceholder(new Label("검색 결과가 없어요"));

        WebView preview = new WebView(); // 미리보기(무음/무자동재생)
        Hyperlink openInYoutube = new Hyperlink("YouTube에서 열기");
        openInYoutube.setVisible(false);
        openInYoutube.setOnAction(e -> {
            MusicItem sel = list.getSelectionModel().getSelectedItem();
            if (sel != null && sel.url != null && !sel.url.isBlank()) {
                try { Desktop.getDesktop().browse(URI.create(sel.url)); } catch (Exception ignored) {}
            }
        });

        // 검색 트리거
        btn.setOnAction(e -> doSearch(search.getText(), list));
        search.setOnAction(e -> btn.fire()); // Enter로 검색

        // ★ 선택 시: 미니플레이어에서 재생 + 우측은 미리보기(autoplay=0)
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            // 미니플레이어에서 실제 재생(다이얼로그 닫아도 계속)
            MiniPlayer.get().play(n.videoId(), n.title);

            // 우측 프리뷰는 자동재생 없이 표시만 (중복 사운드 방지)
            String watchNoAuto = "https://www.youtube.com/watch?v=" + n.videoId();
            preview.getEngine().load(watchNoAuto);

            openInYoutube.setVisible(n.url != null && !n.url.isBlank());
        });

        HBox top = new HBox(8, search, btn);
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

    private void doSearch(String q, ListView<MusicItem> list) {
        if (q == null || q.isBlank()) return;
        try {
            String url = YT_BASE + "/api/yt/search?q=" +
                    URLEncoder.encode(q, StandardCharsets.UTF_8);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();

            client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(body -> {
                        try {
                            var om = new ObjectMapper();
                            Result[] arr = om.readValue(body, Result[].class);

                            MusicItem[] itemsArray = Arrays.stream(arr == null ? new Result[0] : arr)
                                    .map(r -> new MusicItem(r.videoId, unescapeHtml(r.title), r.channel, r.url))
                                    .toArray(MusicItem[]::new);

                            ObservableList<MusicItem> items = FXCollections.observableArrayList(itemsArray);
                            Platform.runLater(() -> list.setItems(items));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Platform.runLater(() -> list.setItems(FXCollections.observableArrayList()));
                        }
                    });
        } catch (Exception ex) {
            ex.printStackTrace();
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

    public static class MusicItem {
        final String videoId; final String title; final String channel; final String url;
        public MusicItem(String videoId, String title, String channel, String url) {
            this.videoId = videoId; this.title = title; this.channel = channel; this.url = url;
        }
        public String videoId() { return videoId; }
        @Override public String toString() { return title + (channel != null ? "  —  " + channel : ""); }
    }
}
