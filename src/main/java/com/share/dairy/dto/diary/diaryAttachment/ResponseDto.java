package com.share.dairy.dto.diary.diaryAttachment;

import com.share.dairy.model.enums.AttachmentType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResponseDto {
    private Long attachmentId;
    private Long entryId;
    private AttachmentType attachmentType;
    private String pathOrUrl;
    private Integer displayOrder;
    private LocalDateTime attachmentCreatedAt;
}
