package com.share.dairy.dto.keyword.characterKeywordImage;

import lombok.Data;

@Data
public class CharacterImageResponse {
    private Long keywordImage;
    private Long analysisId;
    private Long userId;
    private String createdAt;
    private String pathOrUrl;
}
