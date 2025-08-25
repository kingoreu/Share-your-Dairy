// src/main/java/com/share/dairy/dao/keyword/CharacterKeywordImageDao.java
package com.share.dairy.dao.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.keyword.CharacterKeywordImageMapper;
import com.share.dairy.model.keyword.CharacterKeywordImage;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class CharacterKeywordImageDao {
    private final RowMapper<CharacterKeywordImage> mapper = new CharacterKeywordImageMapper();

    public long insert(CharacterKeywordImage e) throws SQLException {
        String sql = """
            INSERT INTO character_keyword_images (analysis_id, user_id, path_or_url, created_at)
            VALUES (?, ?, ?, ?)
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, e.getAnalysisId());
            ps.setLong(2, e.getUserId());
            ps.setString(3, e.getPathOrUrl());
            if (e.getCreatedAt() != null) ps.setTimestamp(4, Timestamp.valueOf(e.getCreatedAt()));
            else ps.setNull(4, Types.TIMESTAMP);

            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public Optional<CharacterKeywordImage> findById(long id) throws SQLException {
        String sql = "SELECT * FROM character_keyword_images WHERE keyword_image = ?";
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<CharacterKeywordImage> findLatestByUserId(long userId) throws SQLException {
        String sql = """
            SELECT * FROM character_keyword_images
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapper.map(rs));
            }
        }
        return Optional.empty();
    }

    public int deleteById(long id) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("DELETE FROM character_keyword_images WHERE keyword_image = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}