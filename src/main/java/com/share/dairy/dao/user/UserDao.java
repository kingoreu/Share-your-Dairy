package com.share.dairy.dao.user;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.user.UserMapper;
import com.share.dairy.model.user.User;
import com.share.dairy.util.DBConnection;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class UserDao {
    private final DataSource ds;
    private final RowMapper<User> mapper = new UserMapper();

    public UserDao(DataSource ds) { this.ds = ds; }
    public Optional<User> findById(long userId) throws SQLException {
        String sql = """
            SELECT user_id, nickname, login_id, password, user_email, character_type,
                   user_created_at, user_updated_at
            FROM users WHERE user_id = ?
        """;
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findByLoginId(String loginId) throws SQLException {
        String sql = """
            SELECT user_id, nickname, login_id, password, user_email, character_type,
                    user_created_at, user_updated_at
            FROM users WHERE login_id = ?
         """;
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, loginId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }

    }

    public long insert(User u) throws SQLException {
        String sql = """
            INSERT INTO users (nickname, login_id, password, user_email, character_type)
            VALUES (?,?,?,?,?)
        """;
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    // login_id 중복 존재 여부
    public boolean existsByLoginId(String loginId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE login_id = ? LIMIT 1";
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, loginId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    // user_email 중복 존재 여부
    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE user_email = ? LIMIT 1";
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }


    public int updateNicknameAndEmail(long userId, String nickname, String email) throws SQLException {
        String sql = "UPDATE users SET nickname=?, user_email=?, user_updated_at=NOW() WHERE user_id=?";
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, nickname);
            ps.setString(2, email);
            ps.setLong(3, userId);
            return ps.executeUpdate();
        }
    }

    // 이메일 / 닉네임 변경 시 레코드 제외하고 중복 체크
    public boolean existsOtherByEmail(long userId, String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE user_email = ? AND user_id <> ? LIMIT 1";
        var con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setLong(2, userId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public int deleteById(long userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id=?";
        Connection con = DataSourceUtils.getConnection(ds);
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate();
        }
    }


}