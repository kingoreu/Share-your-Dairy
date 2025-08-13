package com.share.dairy.dao.user;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.user.UserMapper;
import com.share.dairy.model.user.User;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class UserDao {
    private final RowMapper<User> mapper = new UserMapper();

    public Optional<User> findById(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            return findById(con, userId);
        }
    }

    public Optional<User> findById(Connection con, long userId) throws SQLException {
        String sql = """
            SELECT user_id, nickname, login_id, password, user_email, character_type,
                   user_created_at, user_updated_at
            FROM users WHERE user_id = ?
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findByLoginId(String loginId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
                SELECT user_id, nickname, login_id, password, user_email, character_type,
                       user_created_at, user_updated_at
                FROM users WHERE login_id = ?
            """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setString(1, loginId);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
                }
            }
        }
    }

    public long insert(User u) throws SQLException {
        String sql = """
            INSERT INTO users (nickname, login_id, password, user_email, character_type, user_created_at)
            VALUES (?,?,?,?,?, NOW())
        """;
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNickname());
            ps.setString(2, u.getLoginId());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getUserEmail());
            ps.setString(5, u.getCharacterType());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public int updateNicknameAndEmail(Connection con, long userId, String nickname, String email) throws SQLException {
        String sql = "UPDATE users SET nickname=?, user_email=?, user_updated_at=NOW() WHERE user_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, nickname);
            ps.setString(2, email);
            ps.setLong(3, userId);
            return ps.executeUpdate();
        }
    }
}