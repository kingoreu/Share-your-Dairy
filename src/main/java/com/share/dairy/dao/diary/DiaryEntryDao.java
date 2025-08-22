package com.share.dairy.dao.diary;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class DiaryEntryDao {

    /* 공통 매핑: ResultSet -> DiaryEntry (★ title 포함) */
    private DiaryEntry mapRow(ResultSet rs) throws SQLException {
        DiaryEntry d = new DiaryEntry();
        d.setEntryId(rs.getLong("entry_id"));
        d.setUserId(rs.getLong("user_id"));

        Date dt = rs.getDate("entry_date");
        d.setEntryDate(dt == null ? null : dt.toLocalDate());

        d.setTitle(rs.getString("title"));                 // ★ 반드시 매핑
        d.setDiaryContent(rs.getString("diary_content"));

        String vis = rs.getString("visibility");
        if (vis != null) {
            d.setVisibility(Visibility.valueOf(vis));
        }

        Object shared = rs.getObject("shared_diary_id");
        d.setSharedDiaryId(shared == null ? null : ((Number) shared).longValue());

        // created/updated_at 필드가 모델에 없다면 세팅 생략
        return d;
    }

    public Optional<DiaryEntry> findById(long entryId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findById(con, entryId);
        }
    }

    public Optional<DiaryEntry> findById(Connection con, long entryId) throws SQLException {
        final String sql = """
            SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                   diary_created_at, diary_updated_at, shared_diary_id
            FROM diary_entries
            WHERE entry_id=?
        """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public List<DiaryEntry> findAllByUser(long userId) throws SQLException {
        final String sql = """
            SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                   diary_created_at, diary_updated_at, shared_diary_id
            FROM diary_entries
            WHERE user_id=?
            ORDER BY entry_date DESC, entry_id DESC
        """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DiaryEntry> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /** INSERT도 title 포함 */
    public long insert(DiaryEntry d) throws SQLException {
    String sql = """
      INSERT INTO diary_entries (user_id, entry_date, title, diary_content, visibility, shared_diary_id)
      VALUES (?,?,?,?,?,?)
    """;
    try (var con = DBConnection.getConnection();
         var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        String title = d.getTitle();
        String content = d.getDiaryContent();
        if (title == null || title.isBlank()) {
            String c = (content == null) ? "" : content.replace("\r"," ").replace("\n"," ").trim();
            title = c.isEmpty() ? "" : (c.length() > 30 ? c.substring(0,30) + "…" : c);
        }

        ps.setLong(1, d.getUserId());
        ps.setObject(2, d.getEntryDate());
        ps.setString(3, title);
        ps.setString(4, d.getDiaryContent());
        ps.setString(5, d.getVisibility() == null ? "PRIVATE" : d.getVisibility().name());
        if (d.getSharedDiaryId() == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, d.getSharedDiaryId());

        ps.executeUpdate();
        try (var keys = ps.getGeneratedKeys()) {
            return keys.next() ? keys.getLong(1) : 0L;
        }
    }
}

    /** 본문만 수정 */
    public int updateContent(Connection con, long entryId, String content) throws SQLException {
        final String sql = "UPDATE diary_entries SET diary_content=? WHERE entry_id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }

    public int deleteById(long entryId) throws SQLException {
        final String sql = "DELETE FROM diary_entries WHERE entry_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            return ps.executeUpdate();
        }
    }

    public int updateSharedDiaryId(Connection con, long entryId, Long sharedDiaryId) throws SQLException {
        final String sql = "UPDATE diary_entries SET shared_diary_id=? WHERE entry_id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (sharedDiaryId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, sharedDiaryId);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }

    public List<DiaryEntry> findAllBySharedDiaryId(long sharedDiaryId) throws SQLException {
        final String sql = """
            SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                   diary_created_at, diary_updated_at, shared_diary_id
            FROM diary_entries
            WHERE shared_diary_id=?
            ORDER BY entry_date DESC, entry_id DESC
        """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, sharedDiaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DiaryEntry> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }
}
