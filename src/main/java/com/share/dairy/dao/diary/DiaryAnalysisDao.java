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

    public long upsert(Connection con, DiaryAnalysis a) throws SQLException {
        // entry_id UNIQUE 이므로 UPSERT 패턴
        String sql = """
          INSERT INTO diary_analysis (entry_id, summary, happiness_score, analysis_keywords, analyzed_at)
          VALUES (?,?,?,?, NOW())
          ON DUPLICATE KEY UPDATE summary=VALUES(summary),
                                  happiness_score=VALUES(happiness_score),
                                  analysis_keywords=VALUES(analysis_keywords),
                                  analyzed_at=NOW()
        """;
        try (var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.getEntryId());
            ps.setString(2, a.getSummary());
            if (a.getHappinessScore() == null) ps.setNull(3, Types.TINYINT);
            else ps.setInt(3, a.getHappinessScore());
            ps.setString(4, a.getAnalysisKeywords());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L; // 신규면 키, 업데이트면 0일 수 있음
            }
        }
    }
}
