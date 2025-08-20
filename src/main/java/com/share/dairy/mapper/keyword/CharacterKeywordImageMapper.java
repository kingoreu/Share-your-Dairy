package com.share.dairy.mapper.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.keyword.CharacterKeywordImage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CharacterKeywordImageMapper implements RowMapper<CharacterKeywordImage> {
    @Override public CharacterKeywordImage map(ResultSet rs) throws SQLException {
        var k = new CharacterKeywordImage();
        k.setKeywordImage(rs.getLong("keyword_image"));
        k.setAnalysisId(rs.getLong("analysis_id"));
        k.setUserId(rs.getLong("user_id"));
        var ts = rs.getTimestamp("created_at");
        k.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        return k;
    }
}
