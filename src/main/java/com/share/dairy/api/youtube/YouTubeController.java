package com.share.dairy.api.youtube;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class YouTubeController {

    private final YouTubeService service;

    // Lombok 없이 수동 생성자 주입
    public YouTubeController(YouTubeService service) {
        this.service = service;
    }

    // 헬스 체크
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("ok", "yt");
    }

    // 실제 검색 API
    // 예: GET /api/yt/search?q=iu&max=5
    @GetMapping(value = "/api/yt/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<YouTubeService.VideoDto> search(
            @RequestParam("q") String q,
            @RequestParam(value = "max", required = false, defaultValue = "8") int max
    ) {
        return service.search(q, max); // ← Service 시그니처와 일치
    }
}