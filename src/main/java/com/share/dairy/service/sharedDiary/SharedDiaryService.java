package com.share.dairy.service.sharedDiary;

import com.share.dairy.dao.sharedDiary.SharedDiaryDao;
import com.share.dairy.dao.sharedDiary.SharedDiaryMemberDao;
import com.share.dairy.dto.sharedDiary.SharedDiaryCreateRequest;
import com.share.dairy.dto.sharedDiary.SharedDiaryResponse;
import com.share.dairy.model.sharedDiary.SharedDiary;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
public class SharedDiaryService {

    private final SharedDiaryDao sharedDiaryDao;
    private final SharedDiaryMemberDao memberDao;

    // 두 DAO 모두 생성자 주입
    public SharedDiaryService(SharedDiaryDao sharedDiaryDao,
                              SharedDiaryMemberDao memberDao) {
        this.sharedDiaryDao = sharedDiaryDao;
        this.memberDao = memberDao;
    }

    public Optional<SharedDiary> findById(long id) throws SQLException {
        return sharedDiaryDao.findById(id);
    }

    public List<SharedDiary> findByOwner(long ownerId) throws SQLException {
        return sharedDiaryDao.findByOwner(ownerId);
    }

    // OUR DIARY 카드에 뿌릴 DTO
    public record CardDto(String title, List<String> members, LocalDate startDate) {}

    // 내가 속한 모든 공유일기(오너+멤버) 카드 조회
    public List<CardDto> getCards(long me) throws SQLException {
        return sharedDiaryDao.findCardsForUser(me).stream()
                .map(r -> new CardDto(
                        r.title(),
                        (r.membersCsv() == null || r.membersCsv().isBlank())
                                ? List.of()
                                : Arrays.asList(r.membersCsv().split(",")),
                        r.createdAt().toLocalDateTime().toLocalDate()))
                .toList();
    }

    /** 공유일기 생성 + 멤버 등록까지 한 번에(수동 트랜잭션) */
    public long createWithMembers(SharedDiaryCreateRequest req, List<Long> memberIds) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            boolean prev = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                SharedDiary s = new SharedDiary();
                s.setSharedDiaryTitle(req.getSharedDiaryTitle());
                s.setOwnerId(req.getOwnerId());

                long diaryId = sharedDiaryDao.insert(con, s);

                // 본인 포함 + 중복 제거
                LinkedHashSet<Long> all = new LinkedHashSet<>(memberIds);
                all.add(req.getOwnerId());

                for (Long uid : all) {
                    memberDao.addMember(con, diaryId, uid, req.getOwnerId());
                }

                con.commit();
                con.setAutoCommit(prev);
                return diaryId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ============ 기존 단건 CRUD 유지 ============

    public long create(SharedDiaryCreateRequest req) throws SQLException {
        SharedDiary s = new SharedDiary();
        s.setSharedDiaryTitle(req.getSharedDiaryTitle());
        s.setOwnerId(req.getOwnerId());
        try (Connection con = DBConnection.getConnection()) {
            return sharedDiaryDao.insert(con, s);
        }
    }

    public void updateTitle(long id, String title) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            sharedDiaryDao.updateTitle(con, id, title);
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            sharedDiaryDao.deleteById(con, id);
        }
    }

    // model -> response DTO 변환
    public static SharedDiaryResponse toResponse(SharedDiary s) {
        SharedDiaryResponse dto = new SharedDiaryResponse();
        dto.setSharedDiaryId(s.getSharedDiaryId());
        dto.setSharedDiaryTitle(s.getSharedDiaryTitle());
        dto.setOwnerId(s.getOwnerId());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}