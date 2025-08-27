package com.share.dairy.app.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

import java.awt.Desktop;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MusicDialog {

    // HTTP: 시스템 프록시 사용 + HTTP/1.1 + 타임아웃
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // 로컬 검색 서버(있으면 최우선)
    private static final String YT_BASE = "http://localhost:8090";

    // 환경변수로 인스턴스 지정 가능
    private final String searchApi1 = System.getenv().getOrDefault(
            "YT_SEARCH_API", "https://piped.projectsegfau.lt/api/v1/search?q=");
    private final String searchApi2 = System.getenv().getOrDefault(
            "YT_SEARCH_API_ALT", "https://invidious.privacydev.net/api/v1/search?q=");

    // 강제 HTML 모드 (API 전부 건너뜀)
    private final boolean onlyHtml = "1".equals(System.getenv("YT_ONLY_HTML"));

    private final Consumer<MusicItem> onPick;

    public MusicDialog(Consumer<MusicItem> onPick) {
        this.onPick = onPick;
        System.setProperty("java.net.useSystemProxies", "true");
    }

    private volatile long searchSeq = 0;

    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("MUSIC");

        TextField search = new TextField();
        search.setPromptText("노래/아티스트 검색");
        Button btn = new Button("검색");

        ListView<MusicItem> list = new ListView<>();
        list.setPlaceholder(new Label("검색 결과가 없어요"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(MusicItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setText(null); return; }
                setText(it.title() + (it.channel() != null ? "  —  " + it.channel() : ""));
            }
        });

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

        btn.setOnAction(e -> doSearch(search.getText(), list, loading));
        search.setOnAction(e -> btn.fire());

        HBox top = new HBox(8, search, btn, loading);
        VBox right = new VBox(6, preview, openInYoutube);
        VBox.setVgrow(preview, Priority.ALWAYS);

        SplitPane split = new SplitPane(new StackPane(list), right);
        split.setDividerPositions(0.35);

        VBox root = new VBox(10, top, split);
        root.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (onPick != null) onPick.accept(n);
            preview.getEngine().load("https://www.youtube.com/watch?v=" + n.videoId());
            openInYoutube.setVisible(n.url() != null && !n.url().isBlank());
        });

        dialog.show();
    }

    private void doSearch(String q, ListView<MusicItem> list, ProgressIndicator loading) {
        if (q == null || q.isBlank()) return;
        final long mySeq = ++searchSeq;
        loading.setVisible(true);

        CompletableFuture
                .supplyAsync(() -> requestSearchItems(q))
                .thenAccept(items -> {
                    if (mySeq != searchSeq) return;
                    Platform.runLater(() -> {
                        list.setItems(FXCollections.observableArrayList(items));
                        loading.setVisible(false);
                        if (items.isEmpty()) list.setPlaceholder(new Label("검색 결과가 없어요"));
                    });
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
    }

    /** 로컬 → Piped → Invidious → (최후) YT HTML(ytInitialData) 파싱 */
    private List<MusicItem> requestSearchItems(String query) {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
        ObjectMapper om = new ObjectMapper();

        if (!onlyHtml) {
            List<String> endpoints = Arrays.asList(
                    YT_BASE + "/api/yt/search?q=" + enc + "&max=8",
                    "https://piped.projectsegfau.lt/api/v1/search?q=" + enc + "&region=KR",
                    "https://piped.video/api/v1/search?q=" + enc + "&region=KR",
                    "https://piped.mha.fi/api/v1/search?q=" + enc + "&region=KR",
                    "https://invidious.privacydev.net/api/v1/search?q=" + enc + "&type=video",
                    "https://invidious.nerdvpn.de/api/v1/search?q=" + enc + "&type=video",
                    "https://invidious.flokinet.to/api/v1/search?q=" + enc + "&type=video",
                    searchApi1 + enc,
                    searchApi2 + enc
            );

            for (String url : endpoints) {
                try {
                    System.out.println("[MusicSearch] GET " + url);
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("Accept", "application/json")
                            .header("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                            .GET()
                            .build();

                    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() / 100 != 2) {
                        System.err.println("HTTP " + res.statusCode() + " from " + url);
                        continue;
                    }
                    String ctype = res.headers().firstValue("content-type").orElse("").toLowerCase();
                    if (!ctype.contains("application/json")) {
                        System.err.println("Non-JSON response from " + url + " -> " + ctype);
                        continue;
                    }
                    String body = res.body();

                    if (url.contains("/api/yt/search")) {
                        try {
                            Result[] arr = om.readValue(body, Result[].class);
                            List<MusicItem> mapped = mapResultArray(arr);
                            if (!mapped.isEmpty()) return mapped;
                        } catch (Exception ignore) {}
                    }

                    try {
                        JsonNode node = om.readTree(body);
                        if (node.isArray()) {
                            List<MusicItem> out = new ArrayList<>();
                            for (JsonNode n : node) {
                                MusicItem it = fromPipedNode(n);
                                if (it != null) out.add(it);
                            }
                            if (!out.isEmpty()) return out;
                        }
                    } catch (Exception ignore) {}
                } catch (Exception e) {
                    System.err.println("[MusicSearch] fail " + url + " : " +
                            e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }

        // 최후 폴백: YouTube HTML의 ytInitialData JSON 파싱
        try {
            String ytUrl = "https://www.youtube.com/results?search_query=" + enc + "&hl=ko&gl=KR&persist_gl=1&persist_hl=1";
            System.out.println("[MusicSearch] GET (html) " + ytUrl);
            HttpRequest req = HttpRequest.newBuilder(URI.create(ytUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Accept-Language", "ko,en;q=0.9")
                    .header("Cookie", "CONSENT=YES+1")
                    .GET().build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                JsonNode initial = extractYtInitialData(res.body());
                List<MusicItem> out = new ArrayList<>();
                if (initial != null) {
                    collectVideoRenderers(initial, out, new LinkedHashSet<>(), 12);
                }
                if (!out.isEmpty()) return out;
            } else {
                System.err.println("HTML HTTP " + res.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[MusicSearch] html fail : " + e.getMessage());
        }

        throw new RuntimeException("검색 엔드포인트 실패");
    }

    private static List<MusicItem> mapResultArray(Result[] arr) {
        List<MusicItem> list = new ArrayList<>();
        if (arr == null) return list;
        for (Result r : arr) {
            if (r == null) continue;
            String vid = r.videoId;
            String title = unescapeHtml(r.title);
            String channel = r.channel;
            String url = r.url;
            if ((url == null || url.isBlank()) && vid != null) {
                url = "https://www.youtube.com/watch?v=" + vid;
            }
            if (title != null && !title.isBlank())
                list.add(new MusicItem(vid, title, channel, url));
        }
        return list;
    }

    /** Piped/Invidious 포맷에서 비디오 항목만 매핑 */
    private static MusicItem fromPipedNode(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        String type = text(n, "type");
        if (type != null && !(type.equalsIgnoreCase("video") || type.equalsIgnoreCase("stream")))
            return null;

        String vid = firstText(n, "videoId", "id");
        String title = firstText(n, "title");
        String channel = firstText(n, "author", "uploader", "uploaderName", "channelName");
        String url = firstText(n, "url", "link", "webpageUrl");
        if ((url == null || url.isBlank()) && vid != null)
            url = "https://www.youtube.com/watch?v=" + vid;

        if (title == null || title.isBlank()) return null;
        return new MusicItem(vid, unescapeHtml(title), channel, url);
    }

    /* -------------------- HTML 파싱 (ytInitialData) -------------------- */

    private static JsonNode extractYtInitialData(String html) {
        if (html == null) return null;
        int idx = html.indexOf("ytInitialData");
        if (idx < 0) return null;

        // "ytInitialData" 이후 첫 '{' 위치
        int start = html.indexOf('{', idx);
        if (start < 0) return null;

        int end = findMatchingBrace(html, start);
        if (end < 0) return null;

        String json = html.substring(start, end + 1);
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            System.err.println("[MusicSearch] ytInitialData parse fail: " + e.getMessage());
            return null;
        }
    }

    /** 중괄호 짝 맞추기 (문자열/이스케이프 감안) */
    private static int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        boolean inStr = false;
        char strCh = 0;
        boolean esc = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) { esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == strCh) { inStr = false; continue; }
            } else {
                if (c == '"' || c == '\'') { inStr = true; strCh = c; continue; }
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /** ytInitialData 트리 전체를 재귀 순회하여 videoRenderer 수집 */
    private static void collectVideoRenderers(JsonNode node, List<MusicItem> out, Set<String> seen, int limit) {
        if (node == null || out.size() >= limit) return;

        if (node.has("videoRenderer")) {
            JsonNode vr = node.get("videoRenderer");
            String vid = text(vr, "videoId");
            if (vid != null && !seen.contains(vid)) {
                String title = null;
                JsonNode titleRuns = vr.path("title").path("runs");
                if (titleRuns.isArray() && titleRuns.size() > 0) {
                    title = titleRuns.get(0).path("text").asText();
                }
                String channel = null;
                JsonNode ownerRuns = vr.path("ownerText").path("runs");
                if (ownerRuns.isArray() && ownerRuns.size() > 0) {
                    channel = ownerRuns.get(0).path("text").asText();
                }
                if (title != null && !title.isBlank()) {
                    out.add(new MusicItem(vid, unescapeHtml(title), channel,
                            "https://www.youtube.com/watch?v=" + vid));
                    seen.add(vid);
                }
            }
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext() && out.size() < limit) {
                collectVideoRenderers(it.next().getValue(), out, seen, limit);
            }
        } else if (node.isArray()) {
            for (JsonNode c : node) {
                if (out.size() >= limit) break;
                collectVideoRenderers(c, out, seen, limit);
            }
        }
    }

    private static String text(JsonNode n, String key) {
        if (n == null) return null;
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) return v.asText();
        }
        return null;
    }

    private static String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    // 로컬 서버 포맷
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        public String videoId;
        public String title;
        public String channel;
        public String thumbnailUrl;
        public String url;
    }

    /** 컨트롤러에서 그대로 쓰는 모델 */
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
