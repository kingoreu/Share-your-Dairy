package com.share.dairy.mapper.keyword;

import com.share.dairy.dto.keyword.keywordImage.WithKeywordsDto;
import com.share.dairy.mapper.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KeywordImageWithKeywordsMapper implements RowMapper<WithKeywordsDto> {
    @Override public WithKeywordsDto map(ResultSet rs) throws SQLException {
        var d = new WithKeywordsDto();
        d.setKeywordImage(rs.getLong("keyword_image"));
        d.setAnalysisId(rs.getLong("analysis_id"));
        d.setUserId(rs.getLong("user_id"));
        var ts = rs.getTimestamp("created_at");
        d.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        d.setAnalysisKeywords(rs.getString("analysis_keywords"));
        return d;
    }
}
