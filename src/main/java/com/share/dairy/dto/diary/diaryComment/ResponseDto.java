package com.share.dairy.dto.diary.diaryComment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseDto {
    private Long commentId;
    private Long entryId;
    private Long userId;
    private Long parentId;        // 루트면 null
    private String commentContent;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
