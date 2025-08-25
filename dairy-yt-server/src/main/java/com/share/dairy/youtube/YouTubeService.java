package com.share.dairy.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class YouTubeService {

    @Value("${youtube.api-key:}")
    private String apiKey;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final String SEARCH_FMT =
            "https://www.googleapis.com/youtube/v3/search"
                    + "?part=snippet&type=video"
                    + "&videoEmbeddable=true&videoSyndicated=true"
                    + "&regionCode=KR&relevanceLanguage=ko"
                    + "&maxResults=%d&q=%s"
                    + "&fields=items(id/videoId,snippet(title,channelTitle,thumbnails(high/url,medium/url,default/url)))"
                    + "&key=%s";

    public List<VideoDto> search(String query, int max) {
        String key = apiKey;
        if (key == null || key.isBlank()) {
            String env = System.getenv("YOUTUBE_API_KEY");
            if (env != null && !env.isBlank()) key = env.trim();
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("YOUTUBE_API_KEY가 서버에 설정되어 있지 않습니다.");
        }

        try {
            int capped = Math.max(1, Math.min(max, 20));
            String q = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
            String url = String.format(SEARCH_FMT, capped, q, key);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", UA)
                    .GET()
                    .build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("YouTube API 실패: HTTP " + res.statusCode() + " " + res.body());
            }

            JsonNode items = OM.readTree(res.body()).path("items");
            List<VideoDto> out = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode it : items) {
                    String vid = it.path("id").path("videoId").asText("");
                    if (vid.isBlank()) continue;

                    JsonNode sn = it.path("snippet");
                    String title = sn.path("title").asText("");
                    String channel = sn.path("channelTitle").asText("");

                    // 썸네일 우선순위: high → medium → default
                    String thumb = sn.path("thumbnails").path("high").path("url").asText("");
                    if (thumb.isBlank()) thumb = sn.path("thumbnails").path("medium").path("url").asText("");
                    if (thumb.isBlank()) thumb = sn.path("thumbnails").path("default").path("url").asText("");

                    out.add(new VideoDto(vid, title, channel, thumb, "https://youtu.be/" + vid));
                    if (out.size() >= capped) break;
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record VideoDto(String videoId, String title, String channel, String thumbnailUrl, String url) {}
}
