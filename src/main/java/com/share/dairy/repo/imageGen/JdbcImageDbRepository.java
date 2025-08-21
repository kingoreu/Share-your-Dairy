// src/main/java/com/share/dairy/repo/imageGen/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현.
 *
 * 실제 스키마(현재 DB 기준):
 *  - diary_entries(entry_id, user_id, ...)
 *  - diary_analysis(analysis_id PK, entry_id UNIQUE, analysis_keywords, ...)
 *  - users(user_id, character_type, ...)
 *  - diary_attachments(attachment_id, entry_id, attachment_type, path_or_url, display_order, ...)
 *  - keyword_images(keyword_image PK, analyzed_id, user_id, keywords, created_at)
 *  - character_keyword_images(keyword_image PK, analyzed_id, user_id, keywords, created_at)
 */
@Repository
public class JdbcImageDbRepository implements ImageDbRepository {

    private final JdbcTemplate jdbc;

    public JdbcImageDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntryContext> findContext(long entryId) {
        // 네 군데를 한 번에 조회 (분석이 없으면 Optional.empty)
        String sql = """
            SELECT
                da.analysis_id,              -- 1
                de.user_id,                  -- 2
                da.analysis_keywords,        -- 3
                u.character_type             -- 4
            FROM diary_entries de
              JOIN users u
                ON u.user_id = de.user_id
              LEFT JOIN diary_analysis da
                ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
            """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            long analysisId      = rs.getLong(1);
            long userId          = rs.getLong(2);
            String keywords      = rs.getString(3);
            String characterType = rs.getString(4);
            if (rs.wasNull() || analysisId == 0) return Optional.empty();
            return Optional.of(new EntryContext(analysisId, userId, keywords, characterType));
        }, entryId);
    }

    @Override
    public void upsertAttachment(long entryId, String url, int displayOrder) {
        // 동일 (entry_id, url) 있으면 하나만 유지
        jdbc.update("DELETE FROM diary_attachments WHERE entry_id=? AND path_or_url=?", entryId, url);
        jdbc.update("""
            INSERT INTO diary_attachments
              (entry_id, attachment_type, path_or_url, display_order, attachment_created_at)
            VALUES
              (?, 'IMAGE', ?, ?, NOW())
            """, entryId, url, displayOrder);
    }

    @Override
    public void insertKeywordImageIfAbsent(long analyzedId, long userId, String keywords) {
        // 실제 컬럼명이 analyzed_id + keywords 이므로 그에 맞춰 INSERT
        jdbc.update("""
            INSERT INTO keyword_images (analyzed_id, user_id, keywords, created_at)
            SELECT ?, ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1
              FROM keyword_images
              WHERE analyzed_id = ? AND user_id = ?
            )
            """, analyzedId, userId, keywords, analyzedId, userId);
    }

    @Override
    public void insertCharacterImageIfAbsent(long analyzedId, long userId, String keywords) {
        // character_keyword_images 도 analyzed_id + keywords 컬럼을 사용
        jdbc.update("""
            INSERT INTO character_keyword_images (analyzed_id, user_id, keywords, created_at)
            SELECT ?, ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1
              FROM character_keyword_images
              WHERE analyzed_id = ? AND user_id = ?
            )
            """, analyzedId, userId, keywords, analyzedId, userId);
    }
}
