// src/main/java/com/share/dairy/service/diary/DiaryService.java
package com.share.dairy.service.diary;

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

    public Optional<DiaryEntry> findById(long entryId) throws SQLException {
        return diaryEntryDao.findById(entryId);
    }

    public List<DiaryEntry> findAllByUser(long userId) throws SQLException {
        return diaryEntryDao.findAllByUser(userId);
    }

    public long create(CreateRequest req) throws SQLException {
        var d = new DiaryEntry();
        d.setUserId(req.getUserId());
        d.setEntryDate(req.getEntryDate());
        d.setTitle(req.getTitle());
        d.setDiaryContent(req.getDiaryContent());
        d.setVisibility(req.getVisibility());
        d.setSharedDiaryId(req.getSharedDiaryId()); // 공유 시작과 동시에 등록 가능
        return diaryEntryDao.insert(d);
    }

    public void updateContent(long entryId, String content) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateContent(con, entryId, content);
        }
    }

    public void delete(long entryId) throws SQLException {
        diaryEntryDao.deleteById(entryId);
    }

    // 기존 일기를 특정 공유 일기장에 연결 (공유 시작)
    public void share(long entryId, long sharedDiaryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateSharedDiaryId(con, entryId, sharedDiaryId);
        }
    }

    // 공유 해제 (shared_diary_id = null)
    public void unshare(long entryId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            diaryEntryDao.updateSharedDiaryId(con, entryId, null);
        }
    }

    //공유 일기장에 속한 글 목록 조회
    public List<DiaryEntry> findAllBySharedDiaryId(long sharedDiaryId) throws SQLException {
        return diaryEntryDao.findAllBySharedDiaryId(sharedDiaryId);
    }

    // 모델 → 응답 DTO
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
