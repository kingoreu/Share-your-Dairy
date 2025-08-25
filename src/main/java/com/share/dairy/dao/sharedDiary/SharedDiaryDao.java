package com.share.dairy.dao.sharedDiary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.sharedDiary.SharedDiaryMapper;
import com.share.dairy.model.sharedDiary.SharedDiary;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;
import java.util.*;

@Repository
public class SharedDiaryDao {
    private final RowMapper<SharedDiary> mapper = new SharedDiaryMapper();

    public Optional<SharedDiary> findById(long id) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
                SELECT shared_diary_id, shared_diary_title, owner_id, created_at, updated_at
                FROM shared_diaries WHERE shared_diary_id=?
             """)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public List<SharedDiary> findByOwner(long ownerId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
                SELECT shared_diary_id, shared_diary_title, owner_id, created_at, updated_at
                FROM shared_diaries WHERE owner_id=? ORDER BY created_at DESC
             """)) {
            ps.setLong(1, ownerId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<SharedDiary>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }

    public long insert(Connection con, SharedDiary s) throws SQLException {
        try (var ps = con.prepareStatement("""
            INSERT INTO shared_diaries (shared_diary_title, owner_id) VALUES (?,?)
        """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getSharedDiaryTitle());
            ps.setLong(2, s.getOwnerId());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int updateTitle(Connection con, long id, String title) throws SQLException {
        try (var ps = con.prepareStatement("UPDATE shared_diaries SET shared_diary_title=? WHERE shared_diary_id=?")) {
            ps.setString(1, title);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    public int deleteById(Connection con, long id) throws SQLException {
        try (var ps = con.prepareStatement("DELETE FROM shared_diaries WHERE shared_diary_id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}
