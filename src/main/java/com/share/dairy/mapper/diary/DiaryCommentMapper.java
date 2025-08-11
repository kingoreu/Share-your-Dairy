package com.share.dairy.mapper.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.diary.DiaryComment;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DiaryCommentMapper implements RowMapper<DiaryComment> {
    @Override public DiaryComment map(ResultSet rs) throws SQLException {
        var c = new DiaryComment();
        c.setCommentId(rs.getLong("comment_id"));
        c.setEntryId(rs.getLong("entry_id"));
        c.setUserId(rs.getLong("user_id"));
        long pid = rs.getLong("parent_id");
        c.setParentId(rs.wasNull() ? null : pid);
        c.setCommentContent(rs.getString("comment_content"));
        c.setDeleted(rs.getBoolean("is_deleted"));
        var crt = rs.getTimestamp("created_at");
        c.setCreatedAt(crt == null ? null : crt.toLocalDateTime());
        var upd = rs.getTimestamp("updated_at");
        c.setUpdatedAt(upd == null ? null : upd.toLocalDateTime());
        return c;
    }
}
