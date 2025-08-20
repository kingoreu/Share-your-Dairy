package com.share.dairy.youtube;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeController {
    private final YouTubeService svc;
    public YouTubeController(YouTubeService svc) { this.svc = svc; }

    @GetMapping("/search")
    public List<YouTubeService.VideoDto> search(@RequestParam String q,
                                                @RequestParam(defaultValue = "10") int max) {
        if (q == null || q.trim().isEmpty()) return List.of();
        return svc.search(q.trim(), max);
    }
}
