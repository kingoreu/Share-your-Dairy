package com.share.dairy.job;

import com.share.dairy.service.diary_analysis.DiaryAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisCatchUpJob {

    private final JdbcTemplate jdbc;
    private final DiaryAnalysisService service;

    // 서버 기동 10초 뒤부터 15초 간격으로, 미분석 글 최대 10개씩 처리
    @Scheduled(initialDelay = 10_000, fixedDelay = 15_000)
    public void run() {
        List<Long> ids = jdbc.query("""
            SELECT e.entry_id
            FROM diary_entries e
            LEFT JOIN diary_analysis a ON a.entry_id = e.entry_id
            WHERE a.entry_id IS NULL
            ORDER BY e.entry_id ASC
            LIMIT 10
        """, (rs, i) -> rs.getLong(1));

        if (ids.isEmpty()) return;

        log.info("[analysis][catch-up] pending={}", ids.size());
        for (Long id : ids) {
            try {
                service.process(id);
                log.info("[analysis] ok id={}", id);
            } catch (Exception ex) {
                log.warn("[analysis] fail id={} : {}", id, ex.getMessage());
            }
        }
    }
}
