package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryAttachmentMapper;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DiaryAttachmentDao {

    private final RowMapper<DiaryAttachment> mapper = new DiaryAttachmentMapper();

    /* **********************************************************************
     * 조회 계열
     *  - Controller 에서 기대하는 시그니처: findByEntryId(Connection, Long)
     *  - 기존 findByEntry(long)도 유지(내부적으로 위 메서드 호출)
     * **********************************************************************/

    /** 기존 사용처 호환용(커넥션 내부에서 열기) */
    public List<DiaryAttachment> findByEntry(long entryId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findByEntryId(con, entryId);
        }
    }

    /** 편의 오버로드: 커넥션을 내부에서 열어서 조회 */
    public List<DiaryAttachment> findByEntryId(Long entryId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findByEntryId(con, entryId);
        }
    }

    /** ★ Controller가 기대하는 시그니처: 외부 커넥션을 사용해 조회 */
    public List<DiaryAttachment> findByEntryId(Connection con, Long entryId) throws SQLException {
        String sql = """
            SELECT
                attachment_id,
                entry_id,
                attachment_type,
                path_or_url,
                display_order,
                attachment_created_at
            FROM diary_attachments
            WHERE entry_id = ?
            ORDER BY COALESCE(display_order, 9999), attachment_id
        """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DiaryAttachment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapper.map(rs));
                }
                return list;
            }
        }
    }

    /* **********************************************************************
     * 쓰기/삭제 계열
     * **********************************************************************/

    public long insert(Connection con, DiaryAttachment a) throws SQLException {
        String sql = """
            INSERT INTO diary_attachments (entry_id, attachment_type, path_or_url, display_order)
            VALUES (?,?,?,?)
        """;
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.getEntryId());
            if (a.getAttachmentType() == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, a.getAttachmentType().name());
            ps.setString(3, a.getPathOrUrl());
            if (a.getDisplayOrder() == null) ps.setNull(4, Types.SMALLINT);
            else ps.setInt(4, a.getDisplayOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int deleteById(Connection con, long attachmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM diary_attachments WHERE attachment_id=?")) {
            ps.setLong(1, attachmentId);
            return ps.executeUpdate();
        }
    }
}
