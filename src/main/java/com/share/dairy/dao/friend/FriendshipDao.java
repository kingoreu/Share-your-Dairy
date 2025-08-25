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

    /* ========== 단건 조회 ========== */

    /** 정확한 방향(a->b)으로 단건 조회 */
    public Optional<Friendship> find(Connection con, long a, long b) throws SQLException {
        String sql = """
            SELECT user_id, friend_id, friendship_status, requested_at, responded_at
            FROM friendship WHERE user_id=? AND friend_id=?
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    /** 양방향 중 존재하는 1건을 조회 (a->b 또는 b->a) */
    public Optional<Friendship> findEither(Connection con, long a, long b) throws SQLException {
        String sql = """
            SELECT user_id, friend_id, friendship_status, requested_at, responded_at
            FROM friendship
            WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)
            ORDER BY requested_at DESC
            LIMIT 1
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            ps.setLong(3, b); ps.setLong(4, a);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    /* ========== 생성/변경 ========== */

    /** 보낸 요청(a->b)을 PENDING으로 upsert (유니크 키 필요) */
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

    /** 상대가 보낸 요청(requester->me)에 대해 수락/거절 등 응답 */
    public int respondToIncoming(Connection con, long requesterId, long me, FriendshipStatus status) throws SQLException {
        String sql = "UPDATE friendship SET friendship_status=?, responded_at=NOW() WHERE user_id=? AND friend_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, requesterId);
            ps.setLong(3, me);
            return ps.executeUpdate();
        }
    }

    /** 정확한 방향(a->b)의 상태 변경 */
    public int respond(Connection con, long a, long b, FriendshipStatus status) throws SQLException {
        String sql = "UPDATE friendship SET friendship_status=?, responded_at=NOW() WHERE user_id=? AND friend_id=?";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, a); ps.setLong(3, b);
            return ps.executeUpdate();
        }
    }

    /** 양방향 삭제 */
    public int delete(Connection con, long a, long b) throws SQLException {
        String sql = "DELETE FROM friendship WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)";
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            ps.setLong(3, b); ps.setLong(4, a);
            return ps.executeUpdate();
        }
    }

    /* ========== 목록/조회 ========== */

    /** 나에게 들어온 PENDING (받은 요청들) */
    public List<Friendship> findPendingFor(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
                SELECT user_id, friend_id, friendship_status, requested_at, responded_at
                FROM friendship
                WHERE friend_id=? AND friendship_status='PENDING'
                ORDER BY requested_at DESC
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

    /** 내가 보낸 PENDING (보낸 요청들) */
    public List<Friendship> findOutgoingPending(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
                SELECT user_id, friend_id, friendship_status, requested_at, responded_at
                FROM friendship
                WHERE user_id=? AND friendship_status='PENDING'
                ORDER BY requested_at DESC
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

    /** ACCEPTED 친구 목록 (양방향) */
    public List<Friendship> findFriendsFor(long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
                SELECT user_id, friend_id, friendship_status, requested_at, responded_at
                FROM friendship
                WHERE friendship_status='ACCEPTED'
                  AND (user_id=? OR friend_id=?)
                ORDER BY COALESCE(responded_at, requested_at) DESC
            """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setLong(2, userId);
                try (var rs = ps.executeQuery()) {
                    var list = new ArrayList<Friendship>();
                    while (rs.next()) list.add(mapper.map(rs));
                    return list;
                }
            }
        }
    }

    /** 단방향 ACCEPTED 확인(a->b) */
    public boolean isFriendDirectional(Connection con, long a, long b) throws SQLException {
        String sql = """
            SELECT 1 FROM friendship
            WHERE user_id=? AND friend_id=? AND friendship_status='ACCEPTED'
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** 양방향 ACCEPTED 확인 */
    public boolean areFriends(Connection con, long a, long b) throws SQLException {
        String sql = """
            SELECT 1 FROM friendship
            WHERE friendship_status='ACCEPTED'
              AND ((user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?))
            LIMIT 1
        """;
        try (var ps = con.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b);
            ps.setLong(3, b); ps.setLong(4, a);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** 상태 필터(단방향) — 필요 시 양방향 버전으로 확장 */
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

    /** a->b PENDING 단건 존재 여부(단방향) */
    public Optional<Friendship> findPendingBetween(long a, long b) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            String sql = """
                SELECT user_id, friend_id, friendship_status, requested_at, responded_at
                FROM friendship
                WHERE user_id=? AND friend_id=? AND friendship_status='PENDING'
            """;
            try (var ps = con.prepareStatement(sql)) {
                ps.setLong(1, a); ps.setLong(2, b);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
                }
            }
        }
    }
}
