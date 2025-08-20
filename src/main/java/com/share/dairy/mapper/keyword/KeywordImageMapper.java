package com.share.dairy.mapper.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.keyword.KeywordImage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KeywordImageMapper implements RowMapper<KeywordImage> {
    @Override public KeywordImage map(ResultSet rs) throws SQLException {
        var k = new KeywordImage();
        k.setKeywordImage(rs.getLong("keyword_image"));
        k.setAnalysisId(rs.getLong("analysis_id"));
        k.setUserId(rs.getLong("user_id"));
        var ts = rs.getTimestamp("created_at");
        k.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        return k;
    }
}
