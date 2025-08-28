// src/main/java/com/share/dairy/service/sharedDiary/SharedDiaryService.java
package com.share.dairy.service.sharedDiary;

import com.share.dairy.dao.sharedDiary.SharedDiaryDao;
import com.share.dairy.dao.sharedDiary.SharedDiaryMemberDao;
import com.share.dairy.dto.sharedDiary.SharedDiaryCreateRequest;
import com.share.dairy.dto.sharedDiary.SharedDiaryResponse;
import com.share.dairy.model.sharedDiary.SharedDiary;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SharedDiaryService {

    private final SharedDiaryDao sharedDiaryDao;
    private final SharedDiaryMemberDao memberDao;

    // ✅ 스프링이 사용할 단일 생성자 (명시적으로 @Autowired 붙여도 됨)
    @Autowired
    public SharedDiaryService(SharedDiaryDao sharedDiaryDao,
                              SharedDiaryMemberDao memberDao) {
        this.sharedDiaryDao = sharedDiaryDao;
        this.memberDao = memberDao;
    }

    /** 카드 DTO: 방 진입용 id 포함 */
    public record CardDto(long id, String title, List<String> members, LocalDate startDate) {}

    public Optional<SharedDiary> findById(long id) throws SQLException { return sharedDiaryDao.findById(id); }
    public List<SharedDiary> findByOwner(long ownerId) throws SQLException { return sharedDiaryDao.findByOwner(ownerId); }

    public List<CardDto> getCards(long me) throws SQLException {
        return sharedDiaryDao.findCardsForUser(me).stream()
                .map(r -> new CardDto(
                        r.diaryId(),
                        r.title(),
                        (r.membersCsv() == null || r.membersCsv().isBlank())
                                ? List.of()
                                : List.of(r.membersCsv().split(",")),
                        toLocalDate(r.createdAt())))
                .toList();
    }
    private static LocalDate toLocalDate(Timestamp ts) { return ts == null ? null : ts.toLocalDateTime().toLocalDate(); }

    /** 제목 + 멤버들을 한 번에 생성(트랜잭션) */
    public long createWithMembers(SharedDiaryCreateRequest req, List<Long> memberIds) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            boolean old = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                SharedDiary s = new SharedDiary();
                s.setSharedDiaryTitle(req.getSharedDiaryTitle());
                s.setOwnerId(req.getOwnerId());
                long diaryId = sharedDiaryDao.insert(con, s);

                var all = new java.util.LinkedHashSet<Long>(memberIds);
                all.add(req.getOwnerId());
                for (Long uid : all) memberDao.addMember(con, diaryId, uid, req.getOwnerId());

                con.commit();
                con.setAutoCommit(old);
                return diaryId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    public long create(SharedDiaryCreateRequest req) throws SQLException {
        SharedDiary s = new SharedDiary();
        s.setSharedDiaryTitle(req.getSharedDiaryTitle());
        s.setOwnerId(req.getOwnerId());
        try (var con = DBConnection.getConnection()) {
            return sharedDiaryDao.insert(con, s);
        }
    }

    public void updateTitle(long id, String title) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            sharedDiaryDao.updateTitle(con, id, title);
        }
    }

    public void delete(long id) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            sharedDiaryDao.deleteById(con, id);
        }
    }

    public static SharedDiaryResponse toResponse(SharedDiary s) {
        var dto = new SharedDiaryResponse();
        dto.setSharedDiaryId(s.getSharedDiaryId());
        dto.setSharedDiaryTitle(s.getSharedDiaryTitle());
        dto.setOwnerId(s.getOwnerId());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}