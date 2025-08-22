// src/main/java/com/share/dairy/repo/imageGen/ImageDbRepository.java
package com.share.dairy.repo.imageGen;

import java.util.Optional;

public interface ImageDbRepository {

    /**
     * 이미지 생성 컨텍스트 묶음
     * - analysisId: diary_analysis.analysis_id
     * - userId:     users.user_id
     * - analysisKeywords: diary_analysis.analysis_keywords
     * - characterType:    users.character_type (HAMSTER 등)
     */
    record EntryContext(
            long analysisId,
            long userId,
            String analysisKeywords,
            String characterType
    ) {}

    /** entryId로 위 4가지를 한 번에 조회 */
    Optional<EntryContext> findContext(long entryId);

    /** 다이어리 첨부에 이미지 URL을 기록(동일 URL 있으면 삭제 후 삽입) */
    void upsertAttachment(long entryId, String url, int displayOrder);

    /**
     * keyword_images 에 “한 번만” 생성 기록
     * 컬럼: analyzed_id, user_id, keywords, created_at
     */
    void insertKeywordImageIfAbsent(long analyzedId, long userId, String keywords);

    /**
     * character_keyword_images 에도 “한 번만” 생성 기록
     * 컬럼: analyzed_id, user_id, keywords, created_at
     */
    void insertCharacterImageIfAbsent(long analyzedId, long userId, String keywords);
}
