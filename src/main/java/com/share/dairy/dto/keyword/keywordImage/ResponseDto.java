package com.share.dairy.dto.keyword.keywordImage;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResponseDto {
    private Long id;             // keyword_image PK
    // private Long analysisId;
    // private Long userId;
    private LocalDateTime createdAt;
    private String pathOrUrl;
    // userId랑 analysis는 프론트 요구사항에서 없을 경우 빼도 됨!
}
