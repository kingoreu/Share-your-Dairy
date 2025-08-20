package com.share.dairy.dto.keyword.keywordImage;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResponseDto {
    private Long keywordImage;
    private Long analysisId;
    private Long userId;
    private LocalDateTime createdAt;
}
