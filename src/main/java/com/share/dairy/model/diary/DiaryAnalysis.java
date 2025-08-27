package com.share.dairy.model.diary;

import java.time.LocalDateTime;

public class DiaryAnalysis {
    private Long analysisId;
    private Long entryId;
    private String summary;            // TEXT
    private Integer happinessScore;    // nullable
    private String analysisKeywords;   // TEXT (복수형)
    private LocalDateTime analyzedAt;

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getHappinessScore() { return happinessScore; }
    public void setHappinessScore(Integer happinessScore) { this.happinessScore = happinessScore; }

    public String getAnalysisKeywords() { return analysisKeywords; }
    public void setAnalysisKeywords(String analysisKeywords) { this.analysisKeywords = analysisKeywords; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
