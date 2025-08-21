// src/main/java/com/share/dairy/repository/ImageDbRepository.java
package com.share.dairy.repo.imageGen;

import java.util.Optional;

/**
 * 이미지 생성 워크플로우에 필요한 DB 접근용 DAO.
 * - 키워드/캐릭터 조회 (entry → analysis_keywords, users.character_type)
 * - diary_attachments에 이미지 URL 기록
 * - keyword_images / character_keyword_images에 1회 기록
 */
public interface ImageDbRepository {

    /** 분석/사용자 최소 컨텍스트 (내부 확인용) */
    Optional<EntryContext> findContext(long entryId);

    /** 풀 컨텍스트: 분석 키워드 + 캐릭터 타입까지 한 번에 조회 */
    Optional<FullContext> findFullContext(long entryId);

    /** diary_attachments IMAGE 첨부 upsert(간단: 동일 (entry,url) 삭제 후 삽입) */
    long upsertAttachment(long entryId, String url, int displayOrder);

    /** 분석 기준 기록(중복 삽입 방지) */
    void insertKeywordImageIfAbsent(long analysisId, long userId);
    void insertCharacterImageIfAbsent(long analysisId, long userId);

    // --- 레코드 DTO ---
    record EntryContext(long analysisId, long userId) {}
    record FullContext(long analysisId, long userId, String analysisKeywords, String characterType) {}
}
