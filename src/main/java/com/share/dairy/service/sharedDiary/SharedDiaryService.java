// src/main/java/com/share/dairy/service/shared/SharedDiaryService.java
package com.share.dairy.service.sharedDiary;

import com.share.dairy.dao.sharedDiary.SharedDiaryDao;
import com.share.dairy.dto.sharedDiary.*;
import com.share.dairy.model.sharedDiary.SharedDiary;
import com.share.dairy.util.DBConnection;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class SharedDiaryService {

    private final SharedDiaryDao sharedDiaryDao;

    public SharedDiaryService(SharedDiaryDao sharedDiaryDao) {
        this.sharedDiaryDao = sharedDiaryDao;
    }

    public Optional<SharedDiary> findById(long id) throws SQLException {
        return sharedDiaryDao.findById(id);
    }

    public List<SharedDiary> findByOwner(long ownerId) throws SQLException {
        return sharedDiaryDao.findByOwner(ownerId);
    }

    // 공유일기 생성
    public long create(SharedDiaryCreateRequest req) throws SQLException {
        SharedDiary s = new SharedDiary();
        s.setSharedDiaryTitle(req.getSharedDiaryTitle());
        s.setOwnerId(req.getOwnerId());
        try (var con = DBConnection.getConnection()) {
            return sharedDiaryDao.insert(con, s);
        }
    }

    // 공유일기 제목 수정
    // 다른 field 도 추가될 수 있음
    public void updateTitle(long id, String title) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            sharedDiaryDao.updateTitle(con, id, title);
        }
    }

    // 공유일기 삭제
    public void delete(long id) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            sharedDiaryDao.deleteById(con, id);
        }
    }

    // model → response DTO
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
