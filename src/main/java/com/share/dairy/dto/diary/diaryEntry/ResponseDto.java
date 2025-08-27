package com.share.dairy.dto.diary.diaryEntry;

import com.share.dairy.model.enums.Visibility;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ResponseDto {
    private Long entryId;
    private Long userId;
    private LocalDate entryDate;
    private String title;
    private String diaryContent;
    private Visibility visibility;
    private LocalDateTime diaryCreatedAt;
    private LocalDateTime diaryUpdatedAt;
    private Long sharedDiaryId; // null이면 미공유
}
