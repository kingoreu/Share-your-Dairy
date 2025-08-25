// src/main/java/com/share/dairy/repo/imageGen/ImageDbRepository.java
package com.share.dairy.repo.imageGen;

import java.util.Optional;

/**
 * 이미지 생성/저장에 필요한 DB 접근 인터페이스.
 *
 * ✅ 이 프로젝트의 정책
 *  - 이미지 경로는 diary_attachments가 아니라
 *    keyword_images / character_keyword_images 테이블에만 저장한다.
 */
public interface ImageDbRepository {

    /**
     * 이미지 생성 컨텍스트 묶음
     * - analysisId       : diary_analysis.analysis_id (분석 PK)
     * - userId           : users.user_id
     * - analysisKeywords : diary_analysis.analysis_keywords (프롬프트용, 테이블에 따로 저장하지 않음)
     * - characterType    : users.character_type (예: HAMSTER)
     */
    record EntryContext(
            long analysisId,
            long userId,
            String analysisKeywords,
            String characterType
    ) {}

    /**
     * entryId로 컨텍스트 조회
     * - 분석이 아직 없으면 Optional.empty() 반환
     */
    Optional<EntryContext> findContext(long entryId);

    // ⛔ diary_attachments 사용 종료(호출 금지). 필요하면 삭제해도 무방.
    // void upsertAttachment(long entryId, String url, int displayOrder);

    /**
     * keyword_images에 “한 번만” 생성 기록 (경로 저장)
     * - 유니크키 (analysis_id, user_id) 기반 UPSERT
     * - 존재하면 path_or_url과 생성시각을 갱신
     */
    void insertKeywordImageIfAbsent(long analysisId, long userId, String pathOrUrl);

    /**
     * character_keyword_images에 “한 번만” 생성 기록 (경로 저장)
     * - 유니크키 (analysis_id, user_id) 기반 UPSERT
     * - 존재하면 path_or_url과 생성시각을 갱신
     */
    void insertCharacterImageIfAbsent(long analysisId, long userId, String pathOrUrl);
}