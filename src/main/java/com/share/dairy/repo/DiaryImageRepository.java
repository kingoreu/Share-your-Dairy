package com.share.dairy.repo;

import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class DiaryImageRepository {

    public Map<LocalDate, String> findKeywordImages(long userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT diary_date, keyword_image
            FROM character_keyword_images
            WHERE user_id = ?
              AND diary_date BETWEEN ? AND ?
        """;

        Map<LocalDate, String> map = new HashMap<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("diary_date").toLocalDate();
                    String url = rs.getString("keyword_image");
                    map.put(date, url);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace(); // TODO: 로거로 교체 권장
        }

        return map;
    }
}
