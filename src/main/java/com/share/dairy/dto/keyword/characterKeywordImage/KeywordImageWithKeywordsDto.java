package com.share.dairy.dto.keyword.characterKeywordImage;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KeywordImageWithKeywordsDto {
    // 조인 결과용
    private Long keywordImage;
    private Long analysisId;
    private Long userId;
    private LocalDateTime createdAt;
    private String analysisKeywords;
}