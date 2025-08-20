// src/main/java/com/share/dairy/repository/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring JDBC(JdbcTemplate) 구현.
 *
 * 참고: 너희 스키마에 맞춰 쿼리를 작성했다.
 *  - diary_entries(entry_id, user_id, ...)
 *  - diary_analysis(entry_id UNIQUE, analysis_id ...)
 *  - diary_attachments(attachment_id, entry_id, path_or_url, display_order ...)
 *  - keyword_images(analysis_id, user_id ...)
 *  - character_keyword_images(analysis_id, user_id ...)
 */
@Repository
public class JdbcImageDbRepository implements ImageDbRepository {

    private final JdbcTemplate jdbc;

    public JdbcImageDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntryContext> findContext(long entryId) {
        // diary_entries 와 diary_analysis를 조인해 analysis_id와 user_id를 가져온다.
        // 분석이 아직 안 되어 있으면 이미지 생성은 막는다(서비스에서 예외 처리).
        String sql = """
            SELECT da.analysis_id, de.user_id
            FROM diary_entries de
            LEFT JOIN diary_analysis da ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
            """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            long analysisId = rs.getLong(1);
            long userId     = rs.getLong(2);
            // analysis_id가 NULL/0이면 아직 분석 없음 → Optional.empty()
            if (rs.wasNull() || analysisId == 0) return Optional.empty();
            return Optional.of(new EntryContext(analysisId, userId));
        }, entryId);
    }

    @Override
    public long upsertAttachment(long entryId, String url, int displayOrder) {
        // 간단한 upsert 전략:
        //   - 같은 (entry_id, path_or_url)의 기존 첨부를 지우고 새로 삽입
        //     (UNIQUE 제약이 없어서 INSERT ... ON DUPLICATE 대신 이 방식 사용)
        jdbc.update("DELETE FROM diary_attachments WHERE entry_id = ? AND path_or_url = ?",
                entryId, url);

        jdbc.update("""
            INSERT INTO diary_attachments (entry_id, attachment_type, path_or_url, display_order, attachment_created_at)
            VALUES (?, 'IMAGE', ?, ?, NOW())
            """, entryId, url, displayOrder);

        // 방금 삽입한 attachment_id를 확인(필요시)
        Long id = jdbc.query("""
            SELECT attachment_id
            FROM diary_attachments
            WHERE entry_id = ? AND path_or_url = ?
            ORDER BY attachment_id DESC
            LIMIT 1
            """, rs -> rs.next() ? rs.getLong(1) : null, entryId, url);

        return id != null ? id : 0L;
    }

    @Override
    public void insertKeywordImageIfAbsent(long analysisId, long userId) {
        // 동일 analysis_id 중복 방지: NOT EXISTS
        jdbc.update("""
            INSERT INTO keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM keyword_images WHERE analysis_id = ?
            )
            """, analysisId, userId, analysisId);
    }

    @Override
    public void insertCharacterImageIfAbsent(long analysisId, long userId) {
        // 동일 analysis_id 중복 방지: NOT EXISTS
        jdbc.update("""
            INSERT INTO character_keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM character_keyword_images WHERE analysis_id = ?
            )
            """, analysisId, userId, analysisId);
    }
}
