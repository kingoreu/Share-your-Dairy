package com.share.dairy.repo;

import com.share.dairy.util.DBConnection; 
import com.share.dairy.model.mood.MoodPoint;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MoodRepository {

    /**
     * [핵심 SQL]
     * diary_entries e  (entry_id, user_id, diary_created_at ...)
     * diary_analysis a (entry_id, happiness_score ...)
     * 같은 entry_id 를 조인하여 하루 단위 평균 행복도 구함.
     */
    private static final String SQL =
        """
        SELECT DATE(e.diary_created_at) AS the_date,
               ROUND(AVG(a.happiness_score)) AS avg_score
          FROM diary_entries e
          JOIN diary_analysis a ON a.entry_id = e.entry_id
            WHERE e.user_id = ?
           AND e.diary_created_at >= ?
           AND e.diary_created_at <  ?
         GROUP BY DATE(e.diary_created_at)
         ORDER BY the_date
        """;

    public List<MoodPoint> findDailyMood(long userId, LocalDate from, LocalDate toExclusive) throws Exception {
        List<MoodPoint> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();  
             PreparedStatement ps = con.prepareStatement(SQL)) {

            ps.setLong(1, userId);
            ps.setDate(2, Date.valueOf(from));        // inclusive
            ps.setDate(3, Date.valueOf(toExclusive)); // exclusive

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("the_date").toLocalDate();
                    int score   = rs.getInt("avg_score"); // 1~10 가정
                    result.add(new MoodPoint(d, score));
                }
            }
        }
        return result;
    }
}