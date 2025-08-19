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
                    String url = rs.getString("keyword_image");
                    map.put(date, url);
                }
            }
        // 예외 처리: SQLException 발생 시 스택 트레이스를 출력 (로깅으로 교체 권장)
        } catch (SQLException e) {
            e.printStackTrace(); // TODO: 로거로 교체 권장
        }
         // 반환: 날짜와 이미지 URL의 맵을 반환  
        return map;
    }
}
