package com.share.dairy.dto.sharedDiary;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SharedDiaryResponse {
    private Long sharedDiaryId;
    private String sharedDiaryTitle;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
