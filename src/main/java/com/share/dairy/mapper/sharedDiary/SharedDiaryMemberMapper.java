package com.share.dairy.mapper.sharedDiary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.sharedDiary.SharedDiaryMember;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SharedDiaryMemberMapper implements RowMapper<SharedDiaryMember> {
    @Override
    public SharedDiaryMember map(ResultSet rs) throws SQLException {
        var m = new SharedDiaryMember();
        m.setSharedDiaryId(rs.getLong("shared_diary_id"));
        m.setUserId(rs.getLong("user_id"));
        m.setOwnerId(rs.getLong("owner_id"));
        var c = rs.getTimestamp("created_at");
        m.setCreatedAt(c == null ? null : c.toLocalDateTime());
        return m;
    }
}
