package com.share.dairy.model.sharedDiary;

import java.time.LocalDateTime;

public class SharedDiary {
    private Long sharedDiaryId;
    private String sharedDiaryTitle;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt; // nullable

    public Long getSharedDiaryId() { return sharedDiaryId; }
    public void setSharedDiaryId(Long sharedDiaryId) { this.sharedDiaryId = sharedDiaryId; }

    public String getSharedDiaryTitle() { return sharedDiaryTitle; }
    public void setSharedDiaryTitle(String sharedDiaryTitle) { this.sharedDiaryTitle = sharedDiaryTitle; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

