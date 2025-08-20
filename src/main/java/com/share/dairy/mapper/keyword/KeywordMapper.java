package com.share.dairy.mapper.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.keyword.Keyword;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KeywordMapper implements RowMapper<Keyword> {
    @Override
    public Keyword map(ResultSet rs) throws SQLException {
        var k = new Keyword();
        k.setKeywordId(rs.getLong("keyword_id"));
        k.setKeywordName(rs.getString("keyword_name"));
        return k;
    }
}
