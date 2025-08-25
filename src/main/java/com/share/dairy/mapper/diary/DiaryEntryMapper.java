// com/share/dairy/mapper/DiaryEntryMapper.java
package com.share.dairy.mapper.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DiaryEntryMapper implements RowMapper<DiaryEntry> {
    @Override public DiaryEntry map(ResultSet rs) throws SQLException {
        var d = new DiaryEntry();
        d.setEntryId(rs.getLong("entry_id"));
        d.setUserId(rs.getLong("user_id"));
        var date = rs.getDate("entry_date");
        d.setEntryDate(date == null ? null : date.toLocalDate());
        d.setTitle(rs.getString("title"));
        d.setDiaryContent(rs.getString("diary_content"));
        var vis = rs.getString("visibility");
        d.setVisibility(vis == null ? null : Visibility.valueOf(vis));
        var c = rs.getTimestamp("diary_created_at");
        d.setDiaryCreatedAt(c == null ? null : c.toLocalDateTime());
        var u = rs.getTimestamp("diary_updated_at");
        d.setDiaryUpdatedAt(u == null ? null : u.toLocalDateTime());
        long sdi = rs.getLong("shared_diary_id");
        d.setSharedDiaryId(rs.wasNull() ? null : sdi);
        return d;
    }
}
