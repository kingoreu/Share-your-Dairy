package com.share.dairy.dto.diary.diaryComment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRequest {
    @NotBlank
    @Size(max = 2000)
    private String commentContent;
}