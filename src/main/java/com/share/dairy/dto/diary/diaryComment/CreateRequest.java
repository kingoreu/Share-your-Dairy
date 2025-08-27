package com.share.dairy.dto.diary.diaryComment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRequest {
    @NotBlank
    @Size(max = 2000)        // 필요시 조정
    private String commentContent;

    // null 허용. 값이 있다면 0 이상(보통은 1 이상인데, 0도 허용하려면 이대로, 아니면 @Positive)
    // 대댓글 검증 로직
    @PositiveOrZero
    private Long parentId;
}
