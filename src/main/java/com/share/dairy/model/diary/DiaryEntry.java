package com.share.dairy.model.diary;

import com.share.dairy.model.enums.Visibility;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DiaryEntry {
    private Long entryId;
    private Long userId;
    private LocalDate entryDate;
    private String diaryContent;
    private Visibility visibility;     // ENUM
    private LocalDateTime diaryCreatedAt;
    private LocalDateTime diaryUpdatedAt;
    private Long sharedDiaryId;        // nullable

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public String getDiaryContent() { return diaryContent; }
    public void setDiaryContent(String diaryContent) { this.diaryContent = diaryContent; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public LocalDateTime getDiaryCreatedAt() { return diaryCreatedAt; }
    public void setDiaryCreatedAt(LocalDateTime diaryCreatedAt) { this.diaryCreatedAt = diaryCreatedAt; }

    public LocalDateTime getDiaryUpdatedAt() { return diaryUpdatedAt; }
    public void setDiaryUpdatedAt(LocalDateTime diaryUpdatedAt) { this.diaryUpdatedAt = diaryUpdatedAt; }

    public Long getSharedDiaryId() { return sharedDiaryId; }
    public void setSharedDiaryId(Long sharedDiaryId) { this.sharedDiaryId = sharedDiaryId; }
}
