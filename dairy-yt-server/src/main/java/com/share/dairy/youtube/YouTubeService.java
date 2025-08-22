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
import java.util.ArrayList;
import java.util.List;

@Service
public class YouTubeService {

   @Value("${youtube.api-key:}")
    private String apiKey;

     private static final String SEARCH =
        "https://www.googleapis.com/youtube/v3/search" +
        "?part=snippet&type=video" +
        "&videoEmbeddable=true" +
        "&videoSyndicated=true" +         // ✅ 외부 사이트 재생 허용 영상만
        "&maxResults=%d&q=%s&key=%s";

    public List<VideoDto> search(String query, int max) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("YOUTUBE_API_KEY가 서버에 설정되어 있지 않습니다.");
        }
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(SEARCH, Math.min(Math.max(max,1),20), q, apiKey);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("YouTube API 실패: HTTP " + res.statusCode() + " " + res.body());
            }

            ObjectMapper om = new ObjectMapper();
            JsonNode items = om.readTree(res.body()).path("items");
            List<VideoDto> out = new ArrayList<>();
            for (JsonNode it : items) {
                String vid = it.path("id").path("videoId").asText("");
                JsonNode sn = it.path("snippet");
                String title = sn.path("title").asText("");
                String channel = sn.path("channelTitle").asText("");
                String thumb = sn.path("thumbnails").path("default").path("url").asText("");
                if (!vid.isBlank()) out.add(new VideoDto(vid, title, channel, thumb, "https://youtu.be/" + vid));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record VideoDto(String videoId, String title, String channel, String thumbnailUrl, String url) {}
}
