package com.share.dairy.repo;

import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiaryImageRepository {

    public Map<LocalDate, String> findKeywordImages(long userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT de.entry_date         AS diary_date,
                   cki.path_or_url       AS url
            FROM character_keyword_images cki
            JOIN diary_analysis          da ON da.analysis_id = cki.analysis_id
            JOIN diary_entries           de ON de.entry_id    = da.entry_id
            WHERE cki.user_id = ?
              AND de.entry_date BETWEEN ? AND ?
            ORDER BY de.entry_date ASC, cki.created_at DESC
        """;

        // 날짜 오름차순으로, 같은 날짜에 여러 장이면 "가장 최근(created_at DESC)" 1장만 선택
        Map<LocalDate, String> map = new LinkedHashMap<>();

        // DB 연결 및 쿼리 실행
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // 파라미터 설정
            // userId: 사용자 ID, from: 시작 날짜, to: 종료 날짜
            ps.setLong(1, userId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));

            // PreparedStatement와 ResultSet을 사용하여 쿼리 실행
            // userId, from, to 파라미터를 설정하고 결과를 Map에 저장
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("diary_date").toLocalDate();
                    String url = rs.getString("url");
                    map.putIfAbsent(date, url); // 같은 날짜는 첫 행(가장 최신)만 채택
                }
            }
        // 예외 처리: SQLException 발생 시 스택 트레이스를 출력 (로깅으로 교체 권장)
        } catch (SQLException e) {
             throw new RuntimeException("findKeywordImages() 쿼리 실패", e);
        }
         // 반환: 날짜와 이미지 URL의 맵을 반환  
        return map;
    }
}
