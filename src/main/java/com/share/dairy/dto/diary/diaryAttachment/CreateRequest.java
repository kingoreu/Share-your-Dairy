package com.share.dairy.dto.diary.diaryAttachment;

import com.share.dairy.model.enums.AttachmentType;
import lombok.Data;

@Data
public class CreateRequest {
    private AttachmentType attachmentType; // nullable
    private String pathOrUrl;               // nullable
    private Integer displayOrder;           // nullable
}
