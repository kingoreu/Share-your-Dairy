package com.share.dairy.dao.keyword;

import com.share.dairy.dto.keyword.keywordImage.WithKeywordsDto;
import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.keyword.KeywordImageMapper;
import com.share.dairy.mapper.keyword.KeywordImageWithKeywordsMapper;
import com.share.dairy.model.keyword.KeywordImage;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class KeywordImageDao {
    private final RowMapper<KeywordImage> mapper = new KeywordImageMapper();
    private final RowMapper<WithKeywordsDto> joinMapper = new KeywordImageWithKeywordsMapper();

    public long insert(KeywordImage e) throws SQLException {
        String sql = """
            INSERT INTO keyword_images (analysis_id, user_id, created_at)
            VALUES (?, ?, COALESCE(?, NOW()))
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, e.getAnalysisId());
            ps.setLong(2, e.getUserId());
            if (e.getCreatedAt() != null) ps.setTimestamp(3, Timestamp.valueOf(e.getCreatedAt()));
            else ps.setNull(3, Types.TIMESTAMP);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public Optional<KeywordImage> findById(long id) throws SQLException {
        String sql = "SELECT * FROM keyword_images WHERE keyword_image = ?";
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public List<KeywordImage> findByUserId(long userId) throws SQLException {
        String sql = """
            SELECT * FROM keyword_images
            WHERE user_id = ?
            ORDER BY created_at DESC, keyword_image DESC
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                List<KeywordImage> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        }
    }

    // 조인: 이미지 + 분석 키워드
    public List<WithKeywordsDto> findWithKeywordsByUserId(long userId) throws SQLException {
        String sql = """
            SELECT ki.keyword_image, ki.analysis_id, ki.user_id, ki.created_at,
                   da.analysis_keywords
            FROM keyword_images ki
            JOIN diary_analysis da ON da.analysis_id = ki.analysis_id
            WHERE ki.user_id = ?
            ORDER BY ki.created_at DESC, ki.keyword_image DESC
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                List<WithKeywordsDto> out = new ArrayList<>();
                while (rs.next()) out.add(joinMapper.map(rs));
                return out;
            }
        }
    }

    public int deleteById(long id) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("DELETE FROM keyword_images WHERE keyword_image = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}
