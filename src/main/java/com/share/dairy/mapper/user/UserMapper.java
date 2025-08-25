
package com.share.dairy.mapper.user;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.model.user.User;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserMapper implements RowMapper<User> {
    @Override public User map(ResultSet rs) throws SQLException {
        var u = new User();
        u.setUserId(rs.getLong("user_id"));
        u.setNickname(rs.getString("nickname"));
        u.setLoginId(rs.getString("login_id"));
        u.setPassword(rs.getString("password"));
        u.setUserEmail(rs.getString("user_email"));
        u.setCharacterType(rs.getString("character_type"));
        var c = rs.getTimestamp("user_created_at");
        u.setUserCreatedAt(c == null ? null : c.toLocalDateTime());
        var up = rs.getTimestamp("user_updated_at");
        u.setUserUpdatedAt(up == null ? null : up.toLocalDateTime());
        return u;
    }
}
