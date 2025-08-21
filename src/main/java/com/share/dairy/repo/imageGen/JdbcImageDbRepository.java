// src/main/java/com/share/dairy/repo/imageGen/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현.
 *
 * 스키마(너희 DDL 기준):
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

    /**
     * ✔ findContext를 4컬럼(analysisId, userId, keywords, characterType)으로 통일.
     *   - 분석(da)이 없으면 Optional.empty 반환 → 상위에서 "먼저 분석" 요구.
     */
    @Override
    public Optional<EntryContext> findContext(long entryId) {
        String sql = """
            SELECT
                da.analysis_id,           -- 1
                de.user_id,               -- 2
                da.analysis_keywords,     -- 3
                u.character_type          -- 4
            FROM diary_entries de
            JOIN users u               ON u.user_id = de.user_id
            LEFT JOIN diary_analysis da ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
            """;

        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            long   analysisId = rs.getLong(1);
            long   userId     = rs.getLong(2);
            String keywords   = rs.getString(3);
            String ctype      = rs.getString(4);
            // 분석 행 자체가 없다면 이미지 생성은 진행 불가
            if (rs.wasNull() || analysisId == 0) return Optional.empty();
            return Optional.of(new EntryContext(analysisId, userId, keywords, ctype));
        }, entryId);
    }

    /**
     * diary_attachments upsert 단순 구현:
     *  - 같은 (entry_id, url) 있으면 삭제 후 삽입
     *  - display_order로 정렬 우선순위 부여
     */
    @Override
    public void upsertAttachment(long entryId, String url, int displayOrder) {
        jdbc.update("DELETE FROM diary_attachments WHERE entry_id=? AND path_or_url=?", entryId, url);
        jdbc.update("""
            INSERT INTO diary_attachments
              (entry_id, attachment_type, path_or_url, display_order, attachment_created_at)
            VALUES
              (?, 'IMAGE', ?, ?, NOW())
            """, entryId, url, displayOrder);
    }

    /** keyword_images: 없을 때만 1회 생성 기록 */
    @Override
    public void insertKeywordImageIfAbsent(long analysisId, long userId) {
        jdbc.update("""
            INSERT INTO keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM keyword_images WHERE analysis_id = ? AND user_id = ?
            )
            """, analysisId, userId, analysisId, userId);
    }

    /** character_keyword_images: 없을 때만 1회 생성 기록 */
    @Override
    public void insertCharacterImageIfAbsent(long analysisId, long userId) {
        jdbc.update("""
            INSERT INTO character_keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM character_keyword_images WHERE analysis_id = ? AND user_id = ?
            )
            """, analysisId, userId, analysisId, userId);
    }
}
