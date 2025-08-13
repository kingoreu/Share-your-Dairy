package com.share.dairy.dao.friend;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.friend.FriendshipMapper;
import com.share.dairy.model.enums.FriendshipStatus;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class FriendshipDao {
    private final RowMapper<Friendship> mapper = new FriendshipMapper();

    public Optional<Friendship> find(Connection con, long a, long b) throws SQLException {
        String sql = "SELECT user_id, friend_id, friendship_status, requested_at, responded_at " +
                "FROM friendship WHERE user_id=? AND friend_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    public int upsertPending(Connection con, long a, long b) throws SQLException {
        String sql = """
          INSERT INTO friendship (user_id, friend_id, friendship_status, requested_at)
          VALUES (?,?, 'PENDING', NOW())
          ON DUPLICATE KEY UPDATE friendship_status='PENDING', requested_at=NOW(), responded_at=NULL
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            return ps.executeUpdate();
        }
    }

    public int respond(Connection con, long a, long b, FriendshipStatus status) throws SQLException {
        String sql = "UPDATE friendship SET friendship_status=?, responded_at=NOW() WHERE user_id=? AND friend_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, a); ps.setLong(3, b);
            return ps.executeUpdate();
        }
    }

    public List<Friendship> findPendingFor(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = "SELECT user_id, friend_id, friendship_status, requested_at, responded_at " +
                    "FROM friendship WHERE friend_id=? AND friendship_status='PENDING' ORDER BY requested_at DESC";
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<Friendship>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    // 수락된 친구 목록 조회
    public List<Friendship> findFriendsFor(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
            SELECT user_id, friend_id, friendship_status, requested_at, responded_at
            FROM friendship
            WHERE user_id=? AND friendship_status='ACCEPTED'
            ORDER BY responded_at DESC
        """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<Friendship>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    // 사용자 간에 accepted 관계 확인
    public boolean isFriend(Connection con, long a, long b) throws SQLException {
        String sql = """
        SELECT 1 FROM friendship
        WHERE user_id=? AND friend_id=? AND friendship_status='ACCEPTED'
    """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a);
            ps.setLong(2, b);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 친구 양방향 삭제
    public int delete(Connection con, long a, long b) throws SQLException {
        String sql = "DELETE FROM friendship WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)";
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a);
            ps.setLong(2, b);
            ps.setLong(3, b);
            ps.setLong(4, a);
            return ps.executeUpdate();
        }
    }

    // enum 상태 필터링
    public List<Friendship> findByStatus(long userId, FriendshipStatus status) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
            SELECT user_id, friend_id, friendship_status, requested_at, responded_at
            FROM friendship
            WHERE user_id=? AND friendship_status=?
            ORDER BY requested_at DESC
        """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, status.name());
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<Friendship>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    // 해당 사용자에게 이미 보낸 친구 요청이 있는지 확인 (추가하면 좋은거)
    public Optional<Friendship> findPendingBetween(long a, long b) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
            SELECT user_id, friend_id, friendship_status, requested_at, responded_at
            FROM friendship
            WHERE user_id=? AND friend_id=? AND friendship_status='PENDING'
        """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, a);
                ps.setLong(2, b);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
                }
            }
        }
    }

}