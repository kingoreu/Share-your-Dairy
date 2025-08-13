package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryAttachmentMapper;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.model.enums.AttachmentType;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class DiaryAttachmentDao {
    private final RowMapper<DiaryAttachment> mapper = new DiaryAttachmentMapper();

    public List<DiaryAttachment> findByEntry(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
              SELECT attachment_id, entry_id, attachment_type, path_or_url, display_order, attachment_created_at
              FROM diary_attachments WHERE entry_id=? ORDER BY display_order NULLS FIRST, attachment_id
            """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, entryId);
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<DiaryAttachment>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    public long insert(Connection con, DiaryAttachment a) throws SQLException {
        String sql = """
          INSERT INTO diary_attachments (entry_id, attachment_type, path_or_url, display_order)
          VALUES (?,?,?,?)
        """;
        try (var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.getEntryId());
            if (a.getAttachmentType() == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, a.getAttachmentType().name());
            ps.setString(3, a.getPathOrUrl());
            if (a.getDisplayOrder() == null) ps.setNull(4, Types.SMALLINT);
            else ps.setInt(4, a.getDisplayOrder());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int deleteById(Connection con, long attachmentId) throws SQLException {
        try (var ps = con.prepareStatement("DELETE FROM diary_attachments WHERE attachment_id=?")) {
            ps.setLong(1, attachmentId);
            return ps.executeUpdate();
        }
    }
}
