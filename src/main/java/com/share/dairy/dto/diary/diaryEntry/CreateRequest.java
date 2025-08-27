package com.share.dairy.dto.diary.diaryEntry;

import com.share.dairy.model.enums.Visibility;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateRequest {
    private Long userId;
    private LocalDate entryDate;
    private String title;
    private String diaryContent;
    private Visibility visibility;   // PUBLIC/FRIENDS/PRIVATE
    private Long sharedDiaryId;      // 공유 안 하면 null
}