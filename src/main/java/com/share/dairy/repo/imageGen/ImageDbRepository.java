// src/main/java/com/share/dairy/repo/imageGen/ImageDbRepository.java
package com.share.dairy.repo.imageGen;

import java.util.Optional;

/**
 * 이미지 생성 파이프라인이 DB에서 필요로 하는 최소 컨텍스트와
 * 생성 결과를 기록하는 동작을 정의한다.
 *
 * ✔ EntryContext는 "이미지 생성에 반드시 필요한 4가지"를 모두 담는다.
 *   - analysisId       : diary_analysis.analysis_id (키워드 등 분석 결과의 PK)
 *   - userId           : 사용자 ID (첨부/기록에 필요)
 *   - analysisKeywords : 이미지 1 (키워드 일러스트)에 넣을 프롬프트 소재
 *   - characterType    : 이미지 2 (캐릭터 액션)의 캐릭터 타입(HAMSTER, RACCOON, …)
 *
 * ✔ 모든 구현(Jdbc 등)은 이 인터페이스 시그니처만 맞추면 된다.
 */
public interface ImageDbRepository {

    // 이미지 생성에 필요한 모든 값
    record EntryContext(
            long   analysisId,
            long   userId,
            String analysisKeywords,
            String characterType
    ) {}

    /** entryId 한 건에 대해 컨텍스트 조회 (없으면 Optional.empty) */
    Optional<EntryContext> findContext(long entryId);

    /**
     * diary_attachments에 이미지 URL을 upsert한다.
     * - 같은 (entry_id, path_or_url)이 있으면 하나만 유지 (여기서는 삭제 후 삽입 방식)
     * - displayOrder로 키워드(10)/캐릭터(20) 등에 우선순위를 줄 수 있다.
     */
    void upsertAttachment(long entryId, String url, int displayOrder);

    /** keyword_images: analysis 단위로 1회만 생성 기록(없을 때만 insert) */
    void insertKeywordImageIfAbsent(long analysisId, long userId);

    /** character_keyword_images: analysis 단위로 1회만 생성 기록(없을 때만 insert) */
    void insertCharacterImageIfAbsent(long analysisId, long userId);
}
