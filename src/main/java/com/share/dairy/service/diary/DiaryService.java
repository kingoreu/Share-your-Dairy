// src/main/java/com/share/dairy/service/diary/DiaryService.java
package com.share.dairy.service.diary;

import com.share.dairy.auth.UserSession;            // ✅ 추가
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.dto.diary.diaryEntry.CreateRequest;
import com.share.dairy.dto.diary.diaryEntry.ResponseDto;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class DiaryService {

    private final DiaryEntryDao diaryEntryDao;

    public DiaryService(DiaryEntryDao diaryEntryDao) {
        this.diaryEntryDao = diaryEntryDao;
    }

    /* ===================== 공통 유틸 ===================== */

    /** 현재 로그인 userId 없으면 예외 */
    private long requireCurrentUserId() {
        var s = UserSession.get();
        if (s == null) throw new IllegalStateException("로그인 세션이 없습니다.");
        return s.getUserId();
    }

    /** 글 소유자 검증(본인 글만 수정/삭제 허용) */
    private void assertMine(long entryId) throws SQLException {
        long me = requireCurrentUserId();
        long owner = findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일기입니다."))
                .getUserId();
        if (owner != me) throw new SecurityException("내 일기가 아닙니다.");
    }

    /* ===================== 조회 ===================== */

    public Optional<DiaryEntry> findById(long entryId) throws SQLException {
        return diaryEntryDao.findById(entryId);
    }

    /** 특정 사용자(관리/디버그용). 일반 화면은 아래 findAllMine() 사용 */
    public List<DiaryEntry> findAllByUser(long userId) throws SQLException {
        return diaryEntryDao.findAllByUser(userId);
    }

    /** ✅ 현재 로그인한 나의 일기 목록 */
    public List<DiaryEntry> findAllMine() throws SQLException {
        return diaryEntryDao.findAllByUser(requireCurrentUserId());
    }

    /** 공유 일기장 글 목록 */
    public List<DiaryEntry> findAllBySharedDiaryId(long sharedDiaryId) throws SQLException {
        return diaryEntryDao.findAllBySharedDiaryId(sharedDiaryId);
    }

    /* ===================== 생성/수정/삭제 ===================== */

    /** ✅ 생성 시 요청의 userId는 무시하고 현재 세션 userId를 강제 */
    public long create(CreateRequest req) throws SQLException {
        long me = requireCurrentUserId();

        var d = new DiaryEntry();
        d.setUserId(me);                               // ✅ 세션에서 강제 주입
        d.setEntryDate(req.getEntryDate());
        d.setTitle(req.getTitle());
        d.setDiaryContent(req.getDiaryContent());
        d.setVisibility(req.getVisibility());
        d.setSharedDiaryId(req.getSharedDiaryId());

        return diaryEntryDao.save(d);
    }

    public void updateContent(long entryId, String content) throws SQLException {
        assertMine(entryId);                           // ✅ 소유권 체크
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateContent(con, entryId, content);
        }
    }

    public void delete(long entryId) throws SQLException {
        assertMine(entryId);                           // ✅ 소유권 체크
        diaryEntryDao.deleteById(entryId);
    }

    /** 공유 시작 */
    public void share(long entryId, long sharedDiaryId) throws SQLException {
        assertMine(entryId);                           // ✅ 소유권 체크
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateSharedDiaryId(con, entryId, sharedDiaryId);
        }
    }

    /** 공유 해제 */
    public void unshare(long entryId) throws SQLException {
        assertMine(entryId);                           // ✅ 소유권 체크
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateSharedDiaryId(con, entryId, null);
        }
    }

    /* ===================== DTO 변환 ===================== */
    public static ResponseDto toResponse(DiaryEntry e) {
        var r = new ResponseDto();
        r.setEntryId(e.getEntryId());
        r.setUserId(e.getUserId());
        r.setEntryDate(e.getEntryDate());
        r.setTitle(e.getTitle());
        r.setDiaryContent(e.getDiaryContent());
        r.setVisibility(e.getVisibility());
        r.setDiaryCreatedAt(e.getDiaryCreatedAt());
        r.setDiaryUpdatedAt(e.getDiaryUpdatedAt());
        r.setSharedDiaryId(e.getSharedDiaryId());
        return r;
    }
}
