package com.share.dairy.dao.sharedDiary;

import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SharedDiaryRoomDao {

    // 방 목록 한 행 (작성자 닉네임 포함)
    public record EntryRow(
            long entryId,
            long userId,
            String nickname,
            LocalDate entryDate,
            String title,
            String content
    ) {}

    /** 해당 공유방 글 목록 (최신순) */
    public List<EntryRow> findEntries(long sharedDiaryId) throws SQLException {
        String sql = """
            SELECT e.entry_id,
                   e.user_id,
                   u.nickname,
                   e.entry_date,
                   e.title,
                   e.diary_content
            FROM diary_entries e
            JOIN users u ON u.user_id = e.user_id
            WHERE e.shared_diary_id = ?
            ORDER BY e.entry_date DESC, e.entry_id DESC
        """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, sharedDiaryId);
            try (ResultSet rs = ps.executeQuery()) {
                List<EntryRow> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new EntryRow(
                            rs.getLong("entry_id"),
                            rs.getLong("user_id"),
                            rs.getString("nickname"),
                            rs.getDate("entry_date").toLocalDate(),
                            rs.getString("title"),
                            rs.getString("diary_content")
                    ));
                }
                return list;
            }
        }
    }
}