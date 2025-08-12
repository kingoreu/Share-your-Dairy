package com.share.dairy.model.sharedDiary;

import java.time.LocalDateTime;

public class SharedDiaryMember {
    private Long sharedDiaryId;
    private Long userId;
    private Long ownerId;
    private LocalDateTime createdAt;

    public Long getSharedDiaryId() { return sharedDiaryId; }
    public void setSharedDiaryId(Long sharedDiaryId) { this.sharedDiaryId = sharedDiaryId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
