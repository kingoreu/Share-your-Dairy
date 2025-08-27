package com.share.dairy.mapper.sharedDiary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.sharedDiary.SharedDiary;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SharedDiaryMapper implements RowMapper<SharedDiary> {
    @Override
    public SharedDiary map(ResultSet rs) throws SQLException {
        var s = new SharedDiary();
        s.setSharedDiaryId(rs.getLong("shared_diary_id"));
        s.setSharedDiaryTitle(rs.getString("shared_diary_title"));
        s.setOwnerId(rs.getLong("owner_id"));
        var c = rs.getTimestamp("created_at");
        s.setCreatedAt(c == null ? null : c.toLocalDateTime());
        var u = rs.getTimestamp("updated_at");
        s.setUpdatedAt(u == null ? null : u.toLocalDateTime());
        return s;
    }
}
