package com.share.dairy.dao.sharedDiary;

import com.share.dairy.mapper.RowMapper;
import com.share.dairy.mapper.sharedDiary.SharedDiaryMemberMapper;
import com.share.dairy.model.sharedDiary.SharedDiaryMember;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class SharedDiaryMemberDao {
    private final RowMapper<SharedDiaryMember> mapper = new SharedDiaryMemberMapper();

    public List<SharedDiaryMember> findMembers(long sharedDiaryId) throws SQLException {
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement("""
               SELECT shared_diary_id, user_id, owner_id, created_at
               FROM shared_diary_members WHERE shared_diary_id=? ORDER BY created_at ASC
             """)) {
            ps.setLong(1, sharedDiaryId);
            try (var rs = ps.executeQuery()) {
                var list = new ArrayList<SharedDiaryMember>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        }
    }

    public int addMember(Connection con, long sharedDiaryId, long userId, long ownerId) throws SQLException {
        try (var ps = con.prepareStatement("""
           INSERT INTO shared_diary_members (shared_diary_id, user_id, owner_id) VALUES (?,?,?)
        """)) {
            ps.setLong(1, sharedDiaryId);
            ps.setLong(2, userId);
            ps.setLong(3, ownerId);
            return ps.executeUpdate();
        }
    }

    public int removeMember(Connection con, long sharedDiaryId, long userId) throws SQLException {
        try (var ps = con.prepareStatement("""
          DELETE FROM shared_diary_members WHERE shared_diary_id=? AND user_id=?
        """)) {
            ps.setLong(1, sharedDiaryId);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }
}
