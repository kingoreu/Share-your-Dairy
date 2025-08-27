package com.share.dairy.mapper.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.diary.DiaryAnalysis;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiaryAnalysisMapper implements RowMapper<DiaryAnalysis> {
    @Override
    public DiaryAnalysis map(ResultSet rs) throws SQLException {
        var a = new DiaryAnalysis();
        a.setAnalysisId(rs.getLong("analysis_id"));
        a.setEntryId(rs.getLong("entry_id"));
        a.setSummary(rs.getString("summary"));
        int score = rs.getInt("happiness_score");
        a.setHappinessScore(rs.wasNull() ? null : score);
        a.setAnalysisKeywords(rs.getString("analysis_keywords"));
        var at = rs.getTimestamp("analyzed_at");
        a.setAnalyzedAt(at == null ? null : at.toLocalDateTime());
        return a;
    }
}
