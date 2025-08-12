package com.share.dairy.service.friend;

import com.share.dairy.dao.friend.FriendshipDao;
import com.share.dairy.model.enums.FriendshipStatus;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.util.Tx;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

// 트리거 - 트랜잭션 처리를 위한 서비스 레이어 계층을 생성함
// db 설정과 관련한 내용이니, 실제 기능을 만들 때는 관련 서비스 코드 추가 필요.
@Service
public class FriendshipService {

    private final FriendshipDao friendshipDao;

    public FriendshipService(FriendshipDao friendshipDao) {
        this.friendshipDao = friendshipDao;
    }


    // 친구 요청 보내기: 없으면 INSERT, 있으면 PENDING으로 갱신
    public void send(long senderId, long receiverId) throws SQLException {
        Tx.inTx(con -> {
            friendshipDao.upsertPending(con, senderId, receiverId);
            return null; // commit
        });
    }

    // 수락: 동시성 대비 위해 상태 확인 + 잠금, 양방향 ACCEPTED 보장
    public void accept(long receiverId, long senderId) throws SQLException {
        Tx.inTx(con -> {
            // 상태 확인 & 잠금
            try (var ps = con.prepareStatement("""
                SELECT friendship_status FROM friendship
                WHERE user_id=? AND friend_id=? FOR UPDATE
            """)) {
                ps.setLong(1, senderId);
                ps.setLong(2, receiverId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("요청이 존재하지 않음");
                    if (!"PENDING".equals(rs.getString(1))) throw new SQLException("이미 처리된 요청");
                }
            }
            // 정방향 ACCEPTED
            friendshipDao.respond(con, senderId, receiverId, FriendshipStatus.ACCEPTED);
            // 역방향도 보장 (무방향 친구관계)
            try (var ps = con.prepareStatement("""
                INSERT INTO friendship (user_id, friend_id, friendship_status, requested_at, responded_at)
                VALUES (?,?, 'ACCEPTED', NOW(), NOW())
                ON DUPLICATE KEY UPDATE friendship_status='ACCEPTED', responded_at=NOW()
            """)) {
                ps.setLong(1, receiverId);
                ps.setLong(2, senderId);
                ps.executeUpdate();
            }
            return null; // commit
        });
    }

    // 거절
    public void reject(long receiverId, long senderId) throws SQLException {
        Tx.inTx(con -> {
            friendshipDao.respond(con, senderId, receiverId, FriendshipStatus.REJECTED);
            return null; // commit
        });
    }

    public List<Friendship> findPendingFor(long userId) throws SQLException {
        return friendshipDao.findPendingFor(userId);
    }

    public List<Friendship> findFriendsFor(long userId) throws SQLException {
        return friendshipDao.findFriendsFor(userId);
    }
}
