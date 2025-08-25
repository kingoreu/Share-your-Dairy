package com.share.dairy.repo;

import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiaryImageRepository {

    /** created_at(일기 생성일) 기준으로 [from, to] 기간의 날짜별 캐릭터 키워드 이미지 URL */
    public Map<LocalDate, String> findKeywordImages(long userId, LocalDate from, LocalDate to) {
        String sql = """
        SELECT DATE(de.diary_created_at) AS day,   -- ✅ 일기 생성 '날짜'
               cki.path_or_url           AS url
        FROM character_keyword_images cki
        JOIN diary_analysis  da ON da.analysis_id = cki.analysis_id
        JOIN diary_entries   de ON de.entry_id    = da.entry_id
                               AND de.user_id     = cki.user_id  -- 사용자 일치 보장(안전)
        WHERE de.user_id = ?                                       -- 한 번만 바인딩
          AND de.diary_created_at >= ?                             -- from 00:00:00
          AND de.diary_created_at <  ?                             -- to+1 00:00:00 미만
        ORDER BY day ASC, cki.created_at DESC;                     -- 같은 날 최신 1장
    """;

        Map<LocalDate, String> map = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate day = rs.getDate("day").toLocalDate();
                    String url    = rs.getString("url");
                    map.putIfAbsent(day, url); // 같은 날 여러 장이면 가장 최근 1장만
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findKeywordImages() 실패", e);
        }
        return map;
    }
}