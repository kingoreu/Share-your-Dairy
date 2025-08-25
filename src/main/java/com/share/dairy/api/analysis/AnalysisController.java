package com.share.dairy.api.analysis;

import com.share.dairy.service.diary_analysis.DiaryAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final DiaryAnalysisService service;
    private final JdbcTemplate jdbc;

    public AnalysisController(DiaryAnalysisService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    /** 수동 호출용: { "entryId": 30, "content": "텍스트" }  (content 없으면 DB에서 읽음) */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Req req) {
        Long id = req.entryId;
        String content = req.content;

        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "entryId 가 필요합니다."
            ));
        }

        try {
            log.info("analysis.run entryId={} content.len={}", id, content == null ? null : content.length());
            if (content == null || content.isBlank()) {
                service.process(id);            // DB에서 본문 읽어서 분석
            } else {
                service.process(id, content);   // 전달받은 본문으로 분석
            }
            return ResponseEntity.ok(Map.of("ok", true, "entryId", id));
        } catch (Exception e) {
            log.error("analysis.run failed entryId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "analysis_failed",
                            "message", e.getMessage(),
                            "exception", e.getClass().getName()
                    ));
        }
    }

    /** 미분석 entry 목록 확인용 (프론트/수동 점검에 유용) */
    @GetMapping("/pending")
    public ResponseEntity<?> pending() {
        String sql = "SELECT e.entry_id " +
                     "FROM diary_entries e " +
                     "LEFT JOIN diary_analysis a ON a.entry_id = e.entry_id " +
                     "WHERE a.entry_id IS NULL " +
                     "ORDER BY e.entry_id";
        List<Long> ids = jdbc.queryForList(sql, Long.class);
        return ResponseEntity.ok(Map.of("count", ids.size(), "entryIds", ids));
    }

    /**
     * 일괄 실행: 기본은 '아직 분석 안 된 것만' 처리.
     * onlyMissing=false 로 주면 전체를 강제 재분석.
     * limit 로 상한을 두어 부분 배치도 가능.
     *
     * 예) POST /api/analysis/run-all
     * 예) POST /api/analysis/run-all?onlyMissing=false&limit=50
     */
    @PostMapping("/run-all")
    public ResponseEntity<?> runAll(
            @RequestParam(defaultValue = "true") boolean onlyMissing,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            String base = onlyMissing
                    ? "SELECT e.entry_id FROM diary_entries e " +
                      "LEFT JOIN diary_analysis a ON a.entry_id = e.entry_id " +
                      "WHERE a.entry_id IS NULL ORDER BY e.entry_id"
                    : "SELECT entry_id FROM diary_entries ORDER BY entry_id";

            String sql = (limit != null && limit > 0) ? (base + " LIMIT " + limit) : base;
            List<Long> ids = jdbc.queryForList(sql, Long.class);

            int ok = 0;
            List<Map<String, Object>> errors = new ArrayList<>();

            log.info("analysis.run-all start: size={}, onlyMissing={}, limit={}", ids.size(), onlyMissing, limit);

            for (Long id : ids) {
                try {
                    service.process(id);
                    ok++;
                } catch (Exception ex) {
                    log.error("analysis.run-all failed id={}", id, ex);
                    errors.add(Map.of(
                            "id", id,
                            "error", ex.getClass().getSimpleName(),
                            "message", ex.getMessage()
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "requested", ids.size(),
                    "done", ok,
                    "failed", errors.size(),
                    "errors", errors
            ));
        } catch (Exception e) {
            log.error("analysis.run-all fatal", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "run_all_failed",
                            "message", e.getMessage(),
                            "exception", e.getClass().getName()
                    ));
        }
    }

    // ====== DTO ======
    public static class Req {
        public Long entryId;
        public String content;
    }
}
