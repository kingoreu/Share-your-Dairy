package com.share.dairy.dao.diary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.diary.DiaryAnalysisMapper;
import com.share.dairy.model.diary.DiaryAnalysis;
import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.util.Optional;

public class DiaryAnalysisDao {
    private final RowMapper<DiaryAnalysis> mapper = new DiaryAnalysisMapper();

    public Optional<DiaryAnalysis> findByEntryId(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
               SELECT analysis_id, entry_id, summary, happiness_score, analysis_keywords, analyzed_at
               FROM diary_analysis WHERE entry_id=?
             """)) {
            ps.setLong(1, entryId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }
    // diary_entries.entry_id를 분석해서 diary_analysis에 upsert
    // entry_id는 diary_entries 테이블의 PK로, diary_analysis 테이블의 FK
    // diary_analysis 테이블에 entry_id가 없으면 INSERT, 있으면 UPDATE
    // ON DUPLICATE KEY UPDATE로 중복 시에도 PK를 받을 수 있도록 설정   
    public long upsert(Connection con, DiaryAnalysis a) throws SQLException {
    final String sql = """
        INSERT INTO diary_analysis (entry_id, summary, happiness_score, analysis_keywords, analyzed_at)
        VALUES (?, ?, ?, ?, NOW())
        ON DUPLICATE KEY UPDATE
            summary           = VALUES(summary),
            happiness_score   = VALUES(happiness_score),
            analysis_keywords = VALUES(analysis_keywords),
            analyzed_at       = NOW(),
            -- ✅ UPDATE여도 getGeneratedKeys()로 PK를 받을 수 있게 보장
            analysis_id       = LAST_INSERT_ID(analysis_id)
    """;
        // INSERT 후 PK를 반환하는 쿼리
    // ON DUPLICATE KEY UPDATE로 중복 시에도 PK를 받을 수 있도록 설정   
    try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setLong(1, a.getEntryId());
        ps.setString(2, a.getSummary());

        if (a.getHappinessScore() == null) ps.setNull(3, Types.TINYINT);
        else ps.setInt(3, a.getHappinessScore());

        ps.setString(4, a.getAnalysisKeywords());
        ps.executeUpdate();

        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
        }
    }

    // 🔁 혹시 위에서 못 받았을 때를 위한 안전장치 (이 경우는 거의 없음)
    // entry_id로 diary_analysis 테이블에서 analysis_id를 조회
    // 만약 위에서 이미 PK를 받았다면 이 쿼리는 실행되지 않음
    try (PreparedStatement ps2 = con.prepareStatement(
            "SELECT analysis_id FROM diary_analysis WHERE entry_id = ?")) {
        ps2.setLong(1, a.getEntryId());
        try (ResultSet rs = ps2.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        }
    }
    return 0L;
}

}