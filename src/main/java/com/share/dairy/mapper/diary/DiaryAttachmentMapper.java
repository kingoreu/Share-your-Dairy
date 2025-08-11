package com.share.dairy.mapper.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.model.enums.AttachmentType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiaryAttachmentMapper implements RowMapper<DiaryAttachment> {
    @Override
    public DiaryAttachment map(ResultSet rs) throws SQLException {
        var a = new DiaryAttachment();
        a.setAttachmentId(rs.getLong("attachment_id"));
        a.setEntryId(rs.getLong("entry_id"));
        String t = rs.getString("attachment_type");
        a.setAttachmentType(t == null ? null : AttachmentType.valueOf(t));
        a.setPathOrUrl(rs.getString("path_or_url"));
        int order = rs.getInt("display_order");
        a.setDisplayOrder(rs.wasNull() ? null : order);
        var c = rs.getTimestamp("attachment_created_at");
        a.setAttachmentCreatedAt(c == null ? null : c.toLocalDateTime());
        return a;
    }
}
