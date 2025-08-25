package com.share.dairy.dto.sharedDiary;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MemberResponse {
    private Long sharedDiaryId;
    private Long userId;
    private Long ownerId;
    private LocalDateTime createdAt;
}
