package com.share.dairy.repo.imageGen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JdbcTemplate 기반 구현.
 *
 * ✅ 스키마 요약(현재 DB)
 *  - diary_entries(entry_id, user_id, ...)
 *  - diary_analysis(analysis_id PK, entry_id UNIQUE, analysis_keywords, ...)
 *  - users(user_id, character_type, ...)
 *  - keyword_images(keyword_image_id PK, analysis_id, user_id, path_or_url, created_at, UNIQUE(analysis_id,user_id))
 *  - character_keyword_images(keyword_image_id PK, analysis_id, user_id, path_or_url, created_at, UNIQUE(analysis_id,user_id))
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
        // 분석/사용자/키워드/캐릭터타입을 한 번에 조회
        // 분석이 없을 수 있으므로 LEFT JOIN 유지. (없으면 Optional.empty 반환)
        final String sql = """
            SELECT
                da.analysis_id,        -- 1
                de.user_id,            -- 2
                da.analysis_keywords,  -- 3 (프롬프트용)
                u.character_type       -- 4
            FROM diary_entries de
            JOIN users u
              ON u.user_id = de.user_id
            LEFT JOIN diary_analysis da
              ON da.entry_id = de.entry_id
            WHERE de.entry_id = ?
        """;

        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();

            // ⬇️ Long wrapper로 null-safe 하게 읽는다(==> wasNull 문제 회피)
            Long analysisId   = rs.getObject(1, Long.class);
            Long userId       = rs.getObject(2, Long.class);
            String keywords   = rs.getString(3);
            String charType   = rs.getString(4);

            if (analysisId == null) return Optional.empty(); // 아직 분석 전인 경우
            return Optional.of(new EntryContext(analysisId, userId, keywords, charType));
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
