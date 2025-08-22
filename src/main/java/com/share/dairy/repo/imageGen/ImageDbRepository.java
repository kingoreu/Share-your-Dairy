// src/main/java/com/share/dairy/repo/imageGen/ImageDbRepository.java
package com.share.dairy.repo.imageGen;

import java.util.Optional;

/**
 * 이미지 저장/조회 DB 접근 인터페이스
 *
 * 테이블 스키마(요약)
 * - keyword_images(keyword_image PK, analysis_id, user_id, created_at)
 * - character_keyword_images(keyword_image PK, analysis_id, user_id, created_at)
 */
public interface ImageDbRepository {

    /** 이미지 생성 컨텍스트 묶음 */
    record EntryContext(
            long analysisId,       // diary_analysis.analysis_id
            long userId,           // users.user_id
            String analysisKeywords, // diary_analysis.analysis_keywords (생성 프롬프트용)
            String characterType     // users.character_type
    ) {}

    /** entryId로 컨텍스트 조회(분석 없으면 empty) */
    Optional<EntryContext> findContext(long entryId);

    /** 다이어리 첨부에 이미지 URL을 기록(같은 URL 있으면 삭제 후 삽입) */
    void upsertAttachment(long entryId, String url, int displayOrder);

    /**
     * keyword_images에 “한 번만” 생성 기록
     * 컬럼: analysis_id, user_id, created_at
     */
    void insertKeywordImageIfAbsent(long analysisId, long userId);

    /**
     * character_keyword_images에도 “한 번만” 생성 기록
     * 컬럼: analysis_id, user_id, created_at
     */
    void insertCharacterImageIfAbsent(long analysisId, long userId);
}
