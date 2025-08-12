package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryEntryMapper;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.util.*;

// 기본 CRUD 만 구현. 나머지 추가 기능은 알아서 추가
public class DiaryEntryDao {
    private final RowMapper<DiaryEntry> mapper = new DiaryEntryMapper();

    public Optional<DiaryEntry> findById(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            return findById(con, entryId);
        }
    }

    public Optional<DiaryEntry> findById(Connection con, long entryId) throws SQLException {
        String sql = """
          SELECT entry_id, user_id, entry_date, diary_content, visibility,
                 diary_created_at, diary_updated_at, shared_diary_id
          FROM diary_entries WHERE entry_id=?
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public List<DiaryEntry> findAllByUser(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
              SELECT entry_id, user_id, entry_date, diary_content, visibility,
                     diary_created_at, diary_updated_at, shared_diary_id
              FROM diary_entries WHERE user_id=? ORDER BY entry_date DESC
            """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<DiaryEntry>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    public long insert(DiaryEntry d) throws SQLException {
        String sql = """
          INSERT INTO diary_entries (user_id, entry_date, diary_content, visibility, shared_diary_id)
          VALUES (?,?,?,?,?)
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.getUserId());
            ps.setDate(2, java.sql.Date.valueOf(d.getEntryDate()));
            ps.setString(3, d.getDiaryContent());
            ps.setString(4, d.getVisibility().name());
            if (d.getSharedDiaryId() == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, d.getSharedDiaryId());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int updateContent(Connection con, long entryId, String content) throws SQLException {
        String sql = "UPDATE diary_entries SET diary_content=? WHERE entry_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }

    public int deleteById(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = "DELETE FROM diary_entries WHERE entry_id=?";
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, entryId);
                return ps.executeUpdate();
            }
        }
    }
}