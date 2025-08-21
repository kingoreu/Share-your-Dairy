// src/main/java/com/share/dairy/repository/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현.
 * 스키마(네가 준 DDL):
 *  - diary_entries(entry_id, user_id, ...)
 *  - diary_analysis(entry_id UNIQUE, analysis_id, analysis_keywords, ...)
 *  - users(user_id, character_type, ...)
 *  - diary_attachments(attachment_id, entry_id, attachment_type, path_or_url, display_order, ...)
 *  - keyword_images(analysis_id, user_id, ...)
 *  - character_keyword_images(analysis_id, user_id, ...)
 */
@Repository
public class JdbcImageDbRepository implements ImageDbRepository {

    private final JdbcTemplate jdbc;

    public JdbcImageDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntryContext> findContext(long entryId) {
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
            if (rs.wasNull() || analysisId == 0) return Optional.empty(); // 분석 없으면 빈 Optional
            return Optional.of(new EntryContext(analysisId, userId));
        }, entryId);
    }

    @Override
    public Optional<FullContext> findFullContext(long entryId) {
        String sql = """
            SELECT da.analysis_id, de.user_id, da.analysis_keywords, u.character_type
            FROM diary_entries de
            JOIN users u              ON u.user_id = de.user_id
            LEFT JOIN diary_analysis da ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
            """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            long analysisId = rs.getLong(1);
            long userId     = rs.getLong(2);
            String keywords = rs.getString(3);   // 분석 키워드(문장일 수 있음)
            String ctype    = rs.getString(4);   // 예: HAMSTER, RACCOON ...
            if (rs.wasNull() || analysisId == 0) return Optional.empty();
            return Optional.of(new FullContext(analysisId, userId, keywords, ctype));
        }, entryId);
    }

    @Override
    public long upsertAttachment(long entryId, String url, int displayOrder) {
        // 같은 (entry_id, url)이 있으면 하나만 유지: 삭제 후 삽입
        jdbc.update("DELETE FROM diary_attachments WHERE entry_id=? AND path_or_url=?", entryId, url);
        jdbc.update("""
            INSERT INTO diary_attachments (entry_id, attachment_type, path_or_url, display_order, attachment_created_at)
            VALUES (?, 'IMAGE', ?, ?, NOW())
            """, entryId, url, displayOrder);

        // 방금 넣은 id 확인(필요하면 사용)
        Long id = jdbc.query("""
            SELECT attachment_id
            FROM diary_attachments
            WHERE entry_id=? AND path_or_url=?
            ORDER BY attachment_id DESC
            LIMIT 1
            """, rs -> rs.next() ? rs.getLong(1) : null, entryId, url);
        return id != null ? id : 0L;
    }

    @Override
    public void insertKeywordImageIfAbsent(long analysisId, long userId) {
        jdbc.update("""
            INSERT INTO keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (SELECT 1 FROM keyword_images WHERE analysis_id = ?)
            """, analysisId, userId, analysisId);
    }

    @Override
    public void insertCharacterImageIfAbsent(long analysisId, long userId) {
        jdbc.update("""
            INSERT INTO character_keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (SELECT 1 FROM character_keyword_images WHERE analysis_id = ?)
            """, analysisId, userId, analysisId);
    }
}
