// com/share/dairy/mapper/FriendshipMapper.java
package com.share.dairy.mapper.friend;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.model.enums.FriendshipStatus;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FriendshipMapper implements RowMapper<Friendship> {
    @Override public Friendship map(ResultSet rs) throws SQLException {
        var f = new Friendship();
        f.setUserId(rs.getLong("user_id"));
        f.setFriendId(rs.getLong("friend_id"));
        var st = rs.getString("friendship_status");
        f.setFriendshipStatus(st == null ? null : FriendshipStatus.valueOf(st));
        var r = rs.getTimestamp("requested_at");
        f.setRequestedAt(r == null ? null : r.toLocalDateTime());
        var resp = rs.getTimestamp("responded_at");
        f.setRespondedAt(resp == null ? null : resp.toLocalDateTime());
        return f;
    }
}