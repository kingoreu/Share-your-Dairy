package com.share.dairy.dao.sharedDiary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.sharedDiary.SharedDiaryMapper;
import com.share.dairy.model.sharedDiary.SharedDiary;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;
import java.util.*;

@Repository
public class SharedDiaryDao {
    private final RowMapper<SharedDiary> mapper = new SharedDiaryMapper();

    public Optional<SharedDiary> findById(long id) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
                SELECT shared_diary_id, shared_diary_title, owner_id, created_at, updated_at
                FROM shared_diaries WHERE shared_diary_id=?
             """)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public List<SharedDiary> findByOwner(long ownerId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
                SELECT shared_diary_id, shared_diary_title, owner_id, created_at, updated_at
                FROM shared_diaries WHERE owner_id=? ORDER BY created_at DESC
             """)) {
            ps.setLong(1, ownerId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<SharedDiary>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }

    public record CardRow(long diaryId, String title, String membersCsv, Timestamp createdAt) {}

    public List<CardRow> findCardsForUser(long userId) throws SQLException {
        String sql = """
        SELECT sd.shared_diary_id,
               sd.shared_diary_title,
               sd.created_at AS created_at,
               GROUP_CONCAT(DISTINCT u.nickname ORDER BY u.nickname SEPARATOR ',') AS members
        FROM shared_diaries sd
        /* 멤버 표시용: 모든 멤버 조인 */
        LEFT JOIN shared_diary_members sdm_all
               ON sdm_all.shared_diary_id = sd.shared_diary_id
        LEFT JOIN users u
               ON u.user_id = sdm_all.user_id
        /* 필터: 내가 owner 이거나, 내가 멤버인 방만 */
        WHERE sd.owner_id = ? OR sd.shared_diary_id IN (
              SELECT shared_diary_id FROM shared_diary_members WHERE user_id = ?
        )
        GROUP BY sd.shared_diary_id, sd.shared_diary_title, sd.created_at
        ORDER BY sd.created_at DESC
    """;
        try (var con = DBConnection.getConnection();
             var ps  = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<CardRow>();
                while (rs.next()) {
                    list.add(new CardRow(
                            rs.getLong("shared_diary_id"),
                            rs.getString("shared_diary_title"),
                            rs.getString("members"),
                            rs.getTimestamp("created_at")));
                }
                return list;
            }
        }
    }

    public long insert(Connection con, SharedDiary s) throws SQLException {
        try (var ps = con.prepareStatement("""
            INSERT INTO shared_diaries (shared_diary_title, owner_id) VALUES (?,?)
        """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getSharedDiaryTitle());
            ps.setLong(2, s.getOwnerId());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int updateTitle(Connection con, long id, String title) throws SQLException {
        try (var ps = con.prepareStatement("UPDATE shared_diaries SET shared_diary_title=? WHERE shared_diary_id=?")) {
            ps.setString(1, title);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    public int deleteById(Connection con, long id) throws SQLException {
        try (var ps = con.prepareStatement("DELETE FROM shared_diaries WHERE shared_diary_id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}
