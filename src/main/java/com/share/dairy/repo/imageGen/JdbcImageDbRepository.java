// src/main/java/com/share/dairy/repo/imageGen/JdbcImageDbRepository.java
package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현.
 *
 * ✅ 현재 사용하는 테이블
 *  - diary_entries(entry_id PK, user_id, ...)
 *  - diary_analysis(analysis_id PK, entry_id UNIQUE, analysis_keywords, summary, ...)
 *  - users(user_id PK, character_type, ...)
 *  - keyword_images(keyword_image_id PK, analysis_id, user_id, path_or_url, created_at,
 *                   UNIQUE(analysis_id, user_id))
 *  - character_keyword_images(keyword_image_id PK, analysis_id, user_id, path_or_url, created_at,
 *                             UNIQUE(analysis_id, user_id))
 *
 * ✅ 정책
 *  - 더 이상 diary_attachments에는 쓰지 않는다.
 *  - 두 이미지 경로는 각각의 *_images 테이블에만 저장한다.
 */
@Repository
public class JdbcImageDbRepository implements ImageDbRepository {

    private final JdbcTemplate jdbc;

    public JdbcImageDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntryContext> findContext(long entryId) {
        // 분석/사용자/키워드/요약/캐릭터타입을 한 번에 조회
        // 분석이 없을 수 있으므로 LEFT JOIN으로 가져오고, analysis_id가 null이면 empty.
        final String sql = """
            SELECT
                da.analysis_id,        -- 1
                de.user_id,            -- 2
                da.analysis_keywords,  -- 3
                da.summary,            -- 4 ✨ 추가
                u.character_type       -- 5
            FROM diary_entries de
            JOIN users u
              ON u.user_id = de.user_id
            LEFT JOIN diary_analysis da
              ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
        """;

        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();

            Long   analysisId = rs.getObject(1, Long.class);
            Long   userId     = rs.getObject(2, Long.class);
            String keywords   = rs.getString(3);
            String summary    = rs.getString(4);  // ✨ 추가
            String charType   = rs.getString(5);

            if (analysisId == null) return Optional.empty(); // 아직 분석 전
            return Optional.of(new EntryContext(
                    analysisId, userId, keywords, summary, charType
            ));
        }, entryId);
    }

    @Override
    public void insertKeywordImageIfAbsent(long analysisId, long userId, String pathOrUrl) {
        // ✅ UNIQUE(analysis_id, user_id) 제약을 활용한 UPSERT
        jdbc.update("""
            INSERT INTO keyword_images (analysis_id, user_id, path_or_url, created_at)
            VALUES (?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE
              path_or_url = VALUES(path_or_url),
              created_at  = NOW()
        """, analysisId, userId, pathOrUrl);
    }

    @Override
    public void insertCharacterImageIfAbsent(long analysisId, long userId, String pathOrUrl) {
        // ✅ UNIQUE(analysis_id, user_id) 제약을 활용한 UPSERT
        jdbc.update("""
            INSERT INTO character_keyword_images (analysis_id, user_id, path_or_url, created_at)
            VALUES (?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE
              path_or_url = VALUES(path_or_url),
              created_at  = NOW()
        """, analysisId, userId, pathOrUrl);
    }
}