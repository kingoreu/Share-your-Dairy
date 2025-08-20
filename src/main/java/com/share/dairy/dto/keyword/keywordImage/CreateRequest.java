// src/main/java/com/share/dairy/dto/keyword/KeywordImageCreateRequest.java
package com.share.dairy.dto.keyword.keywordImage;

import lombok.Data;

@Data
public class CreateRequest {
    private Long analysisId;
    private Long userId;
    // createdAt은 DB DEFAULT 쓰면 생략
}
