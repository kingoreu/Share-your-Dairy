package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryEntryMapper;
import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryEntryMapper;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class DiaryEntryDao {
    private final RowMapper<DiaryEntry> mapper = new DiaryEntryMapper();

    /* 단건 조회 */
    /* 단건 조회 */
    public Optional<DiaryEntry> findById(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            return findById(con, entryId);
        }
    }
    // 같은 Connection으로 조회 수행 (트랜잭션 안에서 호출)
    // - entryId가 존재하지 않으면 Optional.empty() 반환
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

    /* ✅ 내 글 목록(현재 user_id 전용) */
    /*  내 글 목록(현재 user_id 전용) */
    public List<DiaryEntry> findAllByUser(long userId) throws SQLException {
        String sql = """
            SELECT entry_id, user_id, shared_diary_id, entry_date, title,
                diary_content, visibility, diary_created_at, diary_updated_at
            FROM diary_entries
            WHERE user_id = ?                 --  꼭 있어야 함
            ORDER BY entry_date DESC, entry_id DESC
        """;
        try (var con = DBConnection.getConnection();
             var ps  = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                List<DiaryEntry> list = new java.util.ArrayList<>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }

    // 공유 일기장 글 목록 조회
    public List<DiaryEntry> findAllBySharedDiaryId(long sharedDiaryId) throws SQLException {
        String sql = """
        SELECT entry_id, user_id, shared_diary_id, entry_date, title,
               diary_content, visibility, diary_created_at, diary_updated_at
          FROM diary_entries
         WHERE shared_diary_id = ?
         ORDER BY entry_date DESC, entry_id DESC
    """;
        try (var con = DBConnection.getConnection();
             var ps  = con.prepareStatement(sql)) {
            ps.setLong(1, sharedDiaryId);
            try (var rs = ps.executeQuery()) {
                List<DiaryEntry> list = new java.util.ArrayList<>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }


    /* 저장 */
    public long save(DiaryEntry entry) throws SQLException {
        String sql = """
            INSERT INTO diary_entries
                (user_id, entry_date, title, diary_content, visibility, diary_created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
        """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, entry.getUserId());
            ps.setDate(2, java.sql.Date.valueOf(entry.getEntryDate()));
            ps.setString(3, entry.getTitle());
            ps.setString(4, entry.getDiaryContent());
            ps.setString(5, entry.getVisibility().name()); // ENUM → 문자열

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("일기 저장 실패 (entry_id 생성 안 됨)");
        }
    }

    /* 내용 수정 */
    public int updateContent(Connection con, long entryId, String content) throws SQLException {
        String sql = "UPDATE diary_entries SET diary_content=? WHERE entry_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }

    /* 삭제 */
    public int deleteById(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps  = con.prepareStatement("DELETE FROM diary_entries WHERE entry_id=?")) {
            ps.setLong(1, entryId);
            return ps.executeUpdate();
        }
    }

    /* 공유일기 연결/해제 */
    public int updateSharedDiaryId(Connection con, long entryId, Long sharedDiaryId) throws SQLException {
        String sql = "UPDATE diary_entries SET shared_diary_id=? WHERE entry_id=?";
        try (var ps = con.prepareStatement(sql)) {
            if (sharedDiaryId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, sharedDiaryId);
            ps.setLong(2, entryId);
            return ps.executeUpdate();
        }
    }
}
