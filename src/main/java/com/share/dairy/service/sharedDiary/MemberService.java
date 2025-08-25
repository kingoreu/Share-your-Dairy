package com.share.dairy.service.sharedDiary;

import com.share.dairy.dto.sharedDiary.MemberAddRequest;
import com.share.dairy.dto.sharedDiary.MemberAddRequest;
import com.share.dairy.dto.sharedDiary.MemberResponse;
import com.share.dairy.model.sharedDiary.SharedDiaryMember;
import com.share.dairy.dao.sharedDiary.SharedDiaryMemberDao;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;

import java.lang.reflect.Member;
import java.sql.SQLException;
import java.util.List;

@Service
public class MemberService {

    private final SharedDiaryMemberDao dao;

    public MemberService(SharedDiaryMemberDao dao) {
        this.dao = dao;
    }

    public List<SharedDiaryMember> findMembers(long sharedDiaryId) throws SQLException {
        return dao.findMembers(sharedDiaryId);
    }

    // 공유 다이어리 멤버 추가
    public void addMember(long sharedDiaryId, MemberAddRequest req) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            dao.addMember(con, sharedDiaryId, req.getUserId(), req.getOwnerId());
        }
    }

    // 공유 다이어리 멤버 삭제 (혹시나 몰라서)
    public void removeMember(long sharedDiaryId, long userId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            dao.removeMember(con, sharedDiaryId, userId);
        }
    }

    public static MemberResponse toResponse(SharedDiaryMember e) {
        var dto = new MemberResponse();
        dto.setSharedDiaryId(e.getSharedDiaryId());
        dto.setUserId(e.getUserId());
        dto.setOwnerId(e.getOwnerId());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
