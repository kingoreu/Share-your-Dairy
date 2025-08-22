// src/main/java/com/share/dairy/repo/imageGen/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현 (스키마에 keywords 컬럼 없음)
 *
 * - NOT EXISTS로 “한 번만” 삽입 (스키마 변경 없이 동작)
 * - 동시성까지 완벽히 막으려면 아래 UPSERT 대안 + 유니크 제약 참고
 */
@Repository
public class JdbcImageDbRepository implements ImageDbRepository {

    private final JdbcTemplate jdbc;

    public JdbcImageDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntryContext> findContext(long entryId) {
        final String sql = """
            SELECT
                da.analysis_id,             -- 1: 분석 PK
                de.user_id,                 -- 2: 작성자
                da.analysis_keywords,       -- 3: 프롬프트용 키워드(테이블에 저장하진 않음)
                u.character_type            -- 4: 캐릭터 타입
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
        // (entry_id, url) 동일 건 하나만 유지
        jdbc.update("DELETE FROM diary_attachments WHERE entry_id = ? AND path_or_url = ?", entryId, url);
        jdbc.update("""
            INSERT INTO diary_attachments
              (entry_id, attachment_type, path_or_url, display_order, attachment_created_at)
            VALUES (?, 'IMAGE', ?, ?, NOW())
            """, entryId, url, displayOrder);
    }

    @Override
    public void insertKeywordImageIfAbsent(long analysisId, long userId) {
        // 스키마 변경 없이 동작: NOT EXISTS
        jdbc.update("""
            INSERT INTO keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM keyword_images WHERE analysis_id = ? AND user_id = ?
            )
            """, analysisId, userId, analysisId, userId);

        /*
        // (권장 대안) 유니크 제약 + UPSERT로 동시성까지 안전
        // 1) 1회 실행:
        // ALTER TABLE keyword_images
        //   ADD CONSTRAINT uq_keyword_images_analysis_user UNIQUE (analysis_id, user_id);
        // 2) 쿼리:
        // jdbc.update("""
        //     INSERT INTO keyword_images (analysis_id, user_id, created_at)
        //     VALUES (?, ?, NOW())
        //     ON DUPLICATE KEY UPDATE created_at = NOW()
        // """, analysisId, userId);
        */
    }

    @Override
    public void insertCharacterImageIfAbsent(long analysisId, long userId) {
        // 스키마 변경 없이 동작: NOT EXISTS
        jdbc.update("""
            INSERT INTO character_keyword_images (analysis_id, user_id, created_at)
            SELECT ?, ?, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM character_keyword_images WHERE analysis_id = ? AND user_id = ?
            )
            """, analysisId, userId, analysisId, userId);

        /*
        // (권장 대안) 유니크 제약 + UPSERT
        // ALTER TABLE character_keyword_images
        //   ADD CONSTRAINT uq_character_keyword_images_analysis_user UNIQUE (analysis_id, user_id);
        // jdbc.update("""
        //     INSERT INTO character_keyword_images (analysis_id, user_id, created_at)
        //     VALUES (?, ?, NOW())
        //     ON DUPLICATE KEY UPDATE created_at = NOW()
        // """, analysisId, userId);
        */
    }
}
