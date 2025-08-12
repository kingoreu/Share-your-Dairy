package com.share.dairy.dao.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.keyword.KeywordMapper;
import com.share.dairy.model.keyword.Keyword;
import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.util.*;

public class KeywordDao {
    private final RowMapper<Keyword> mapper = new KeywordMapper();

    public Optional<Keyword> findByName(String name) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
               SELECT keyword_id, keyword_name FROM keywords WHERE keyword_name=?
             """)) {
            ps.setString(1, name);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public long insert(Connection con, String name) throws SQLException {
        try (var ps = con.prepareStatement("""
           INSERT INTO keywords (keyword_name) VALUES (?)
        """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }
}
