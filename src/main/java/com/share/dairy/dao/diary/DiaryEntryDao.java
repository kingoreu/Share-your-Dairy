package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryEntryMapper;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
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
          SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                 diary_created_at, diary_updated_at, shared_diary_id
          FROM diary_entries
          WHERE entry_id=?
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
              SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                     diary_created_at, diary_updated_at, shared_diary_id
              FROM diary_entries
              WHERE user_id=?
              ORDER BY entry_date DESC, entry_id DESC
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

    /** 사용 중일 수도 있으니 INSERT도 title 포함으로 맞춰둡니다. */
    public long insert(DiaryEntry d) throws SQLException {
        String sql = """
          INSERT INTO diary_entries (user_id, entry_date, title, diary_content, visibility, shared_diary_id)
          VALUES (?,?,?,?,?,?)
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.getUserId());
            ps.setObject(2, d.getEntryDate());                          // LocalDate
            ps.setString(3, d.getTitle() == null ? "" : d.getTitle());  // ★ title
            ps.setString(4, d.getDiaryContent());
            ps.setString(5, d.getVisibility() == null ? "PRIVATE" : d.getVisibility().name());
            if (d.getSharedDiaryId() == null) ps.setNull(6, Types.BIGINT);
            else ps.setLong(6, d.getSharedDiaryId());

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

     // 공유일기면 shared_diary_id 추가하여 삽입
    public int updateSharedDiaryId(Connection con, long entryId, Long sharedDiaryId) throws SQLException {
        String sql = "UPDATE diary_entries SET shared_diary_id=? WHERE entry_id=?";
        try (var ps = con.prepareStatement(sql)) {
            if (sharedDiaryId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, sharedDiaryId);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }

    // 공유 일기장에 속한 글 목록
    // 페이징 있을 경우 LIMIT,?,? 와 page, size 같은 파라미터 별도로 추가 필요
    public List<DiaryEntry> findAllBySharedDiaryId(long sharedDiaryId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
                SELECT entry_id, user_id, entry_date, title, diary_content, visibility,
                       diary_created_at, diary_updated_at, shared_diary_id
                FROM diary_entries
                WHERE shared_diary_id=?
                ORDER BY entry_date DESC
             """)) {
            ps.setLong(1, sharedDiaryId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<DiaryEntry>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }


}