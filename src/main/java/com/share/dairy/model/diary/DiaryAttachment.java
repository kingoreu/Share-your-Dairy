package com.share.dairy.model.diary;

import com.share.dairy.model.enums.AttachmentType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DiaryAttachment
 * ------------------------------------------------------------
 * - 일기 첨부(링크/파일 등) 메타 정보를 담는 모델
 * - DB 컬럼 매핑 가정:
 *   attachment_id (PK), entry_id (FK),
 *   attachment_type, path_or_url, display_order, attachment_created_at
 *
 * 사용 팁:
 * - BGM(유튜브 링크)인 경우: isYouTubeLink()로 판별 후 getSafePathOrUrl() 사용
 */
public class DiaryAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long attachmentId;
    private Long entryId;
    /** nullable */
    private AttachmentType attachmentType;
    /** nullable */
    private String pathOrUrl;
    /** nullable */
    private Integer displayOrder;
    /** nullable */
    private LocalDateTime attachmentCreatedAt;

    // ----------------- Constructors -----------------
    public DiaryAttachment() {}

    public DiaryAttachment(Long attachmentId,
                           Long entryId,
                           AttachmentType attachmentType,
                           String pathOrUrl,
                           Integer displayOrder,
                           LocalDateTime attachmentCreatedAt) {
        this.attachmentId = attachmentId;
        this.entryId = entryId;
        this.attachmentType = attachmentType;
        this.pathOrUrl = pathOrUrl;
        this.displayOrder = displayOrder;
        this.attachmentCreatedAt = attachmentCreatedAt;
    }

    // ----------------- Getters / Setters -----------------
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

    // ----------------- Helpers (무해한 편의 메서드) -----------------

    /** 링크형 첨부인지 대략 판별 (타입이 LINK이거나, URL 문자열이 존재하는 경우) */
    public boolean isLink() {
        if (attachmentType != null && "LINK".equalsIgnoreCase(attachmentType.name())) return true;
        return pathOrUrl != null && !pathOrUrl.isBlank();
    }

    /** 유튜브 링크인지 간단 판별 */
    public boolean isYouTubeLink() {
        String u = pathOrUrl;
        if (u == null) return false;
        String s = u.toLowerCase();
        return s.contains("youtube.com") || s.contains("youtu.be");
    }

    /** NPE 방지용 */
    public String getSafePathOrUrl() {
        return pathOrUrl == null ? "" : pathOrUrl;
    }

    // ----------------- Object overrides -----------------
    @Override
    public String toString() {
        return "DiaryAttachment{" +
                "attachmentId=" + attachmentId +
                ", entryId=" + entryId +
                ", attachmentType=" + attachmentType +
                ", pathOrUrl='" + pathOrUrl + '\'' +
                ", displayOrder=" + displayOrder +
                ", attachmentCreatedAt=" + attachmentCreatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiaryAttachment that)) return false;
        // PK 기준 동등성(없으면 객체 동일성으로 fallback)
        return Objects.equals(attachmentId, that.attachmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attachmentId);
    }
}
