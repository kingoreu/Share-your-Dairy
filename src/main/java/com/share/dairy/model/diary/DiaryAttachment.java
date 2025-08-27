package com.share.dairy.model.diary;

import com.share.dairy.model.enums.AttachmentType;
import java.time.LocalDateTime;

public class DiaryAttachment {
    private Long attachmentId;
    private Long entryId;
    private AttachmentType attachmentType; // nullable
    private String pathOrUrl;              // nullable
    private Integer displayOrder;          // nullable
    private LocalDateTime attachmentCreatedAt;

    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long attachmentId) { this.attachmentId = attachmentId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public AttachmentType getAttachmentType() { return attachmentType; }
    public void setAttachmentType(AttachmentType attachmentType) { this.attachmentType = attachmentType; }

    public String getPathOrUrl() { return pathOrUrl; }
    public void setPathOrUrl(String pathOrUrl) { this.pathOrUrl = pathOrUrl; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public LocalDateTime getAttachmentCreatedAt() { return attachmentCreatedAt; }
    public void setAttachmentCreatedAt(LocalDateTime attachmentCreatedAt) { this.attachmentCreatedAt = attachmentCreatedAt; }
}
