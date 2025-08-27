package com.share.dairy.dto.keyword.keywordImage;

import lombok.Data;
import java.time.LocalDateTime;

// 키워드까지 같이 뽑는 조인 결과용
@Data
public class WithKeywordsDto {
    private Long keywordImage;
    private Long analysisId;
    private Long userId;
    private LocalDateTime createdAt;
    private String analysisKeywords; // diary_analysis.analysis_keywords
}
