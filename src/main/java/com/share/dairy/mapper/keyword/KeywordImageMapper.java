package com.share.dairy.mapper.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.keyword.KeywordImage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KeywordImageMapper implements RowMapper<KeywordImage> {
    @Override
    public KeywordImage map(ResultSet rs) throws SQLException {
        var ki = new KeywordImage();
        ki.setMappingId(rs.getLong("mapping_id"));
        ki.setKeywordId(rs.getLong("keyword_id"));
        ki.setImageName(rs.getString("image_name"));
        int order = rs.getInt("display_order");
        ki.setDisplayOrder(rs.wasNull() ? null : order);
        var c = rs.getTimestamp("created_at");
        ki.setCreatedAt(c == null ? null : c.toLocalDateTime());
        return ki;
    }
}
