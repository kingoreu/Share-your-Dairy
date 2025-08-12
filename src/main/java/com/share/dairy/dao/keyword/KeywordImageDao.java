package com.share.dairy.dao.keyword;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.keyword.KeywordImageMapper;
import com.share.dairy.model.keyword.KeywordImage;
import com.share.dairy.util.DBConnection;

import java.sql.*;
import java.util.*;

public class KeywordImageDao {
    private final RowMapper<KeywordImage> mapper = new KeywordImageMapper();

    public List<KeywordImage> findByKeyword(long keywordId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
               SELECT mapping_id, keyword_id, image_name, display_order, created_at
               FROM keyword_images WHERE keyword_id=? ORDER BY display_order, mapping_id
             """)) {
            ps.setLong(1, keywordId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<KeywordImage>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }

    public long insert(Connection con, KeywordImage ki) throws SQLException {
        try (var ps = con.prepareStatement("""
           INSERT INTO keyword_images (keyword_id, image_name, display_order)
           VALUES (?,?,?)
        """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ki.getKeywordId());
            ps.setString(2, ki.getImageName());
            if (ki.getDisplayOrder() == null) ps.setNull(3, Types.SMALLINT);
            else ps.setInt(3, ki.getDisplayOrder());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }
}
