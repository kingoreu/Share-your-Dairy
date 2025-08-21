package com.share.dairy.dto.imageGen;

import com.share.dairy.model.enums.CharacterType;

/** 이미지 생성 API 요청/응답 DTO */
public class ImageGenerateDtos {
    public static class GenerateRequest {
        public String  keyword;      // 예: "야구"
        // String 에서 CharacterType 으로 변경
        public CharacterType character;    // 예: "raccoon" (파일명과 일치)
        public Boolean regenerate;   // true면 강제 재생성
        public String  size;         // "512" | "1024" | "1536"
    }
    public static class GenerateResponse {
        public String keywordImageUrl;
        public String characterImageUrl;
        public GenerateResponse(String k, String c) { keywordImageUrl = k; characterImageUrl = c; }
    }
}
