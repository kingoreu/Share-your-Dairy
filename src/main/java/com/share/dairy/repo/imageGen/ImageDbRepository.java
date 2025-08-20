package com.share.dairy.repo.imageGen;

import java.util.Optional;

public interface ImageDbRepository {

    /** entry_id로 analysis_id와 user_id를 함께 조회 (이미지 기록/첨부 저장에 필요) */
    Optional<EntryContext> findContext(long entryId);

    /** 동일 entry에서 같은 파일명(URL)이 있으면 먼저 지우고 새로 삽입 */
    long upsertAttachment(long entryId, String url, int displayOrder);

    /** 중복 방지: 동일 analysis_id의 기록이 있으면 삽입하지 않음 */
    void insertKeywordImageIfAbsent(long analysisId, long userId);

    /** 중복 방지: 동일 analysis_id의 기록이 있으면 삽입하지 않음 */
    void insertCharacterImageIfAbsent(long analysisId, long userId);

    record EntryContext(long analysisId, long userId) {}
}
