package com.share.dairy.model.keyword;

import java.time.LocalDateTime;

public class CharacterKeywordImage {
    private Long keywordImage;
    private Long analysisId;
    private Long userId;
    private LocalDateTime createdAt;
    private String pathOrUrl;

    public Long getKeywordImage() {
        return keywordImage;
    }

    public void setKeywordImage(Long keywordImage) {
        this.keywordImage = keywordImage;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(Long analysisId) {
        this.analysisId = analysisId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPathOrUrl() { return pathOrUrl; }

    public void setPathOrUrl(String pathOrUrl) { this.pathOrUrl = pathOrUrl; }
}
