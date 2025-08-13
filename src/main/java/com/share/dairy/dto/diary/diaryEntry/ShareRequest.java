package com.share.dairy.dto.diary.diaryEntry;

import lombok.Data;

@Data
public class ShareRequest {
    // 공유 해제는 null 허용 X (해제는 DELETE 엔드포인트로)
    private Long sharedDiaryId;
}
