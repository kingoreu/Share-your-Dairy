package com.share.dairy.service.diary;

import com.share.dairy.dao.diary.DiaryAttachmentDao;
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.util.Tx;

import java.sql.*;
import java.util.List;

// 트리거 - 트랜잭션 처리를 위한 서비스 레이어 계층을 생성함
// db 설정과 관련한 내용이니, 실제 기능을 만들 때는 관련 서비스 코드 추가 필요.
// 다이어리 작성 트랜잭션 처리와 관련한 내용이므로 따로 작성.
public class DiaryWriteService {
    private final DiaryEntryDao diaryEntryDao = new DiaryEntryDao();
    private final DiaryAttachmentDao diaryAttachmentDao = new DiaryAttachmentDao();

    // 본문 + 첨부 N개를 모두 성공해야만 commit
    public long createWithAttachments(DiaryEntry entry, List<DiaryAttachment> attachments) throws SQLException {
        return Tx.inTx(con -> {
            // DiaryEntryDao에 (Connection, …) 버전이 없다면 하나 추가해줘야 함
            long entryId = insertEntry(con, entry);
            for (var a : attachments) {
                a.setEntryId(entryId);
                diaryAttachmentDao.insert(con, a);
            }
            return entryId; // commit
        });
    }

    // 필요 시 DiaryEntryDao에 동일 로직을 이동해도 OK
    private long insertEntry(Connection con, DiaryEntry d) throws SQLException {
        String sql = """
          INSERT INTO diary_entries (user_id, entry_date, diary_content, visibility, shared_diary_id)
          VALUES (?,?,?,?,?)
        """;
        try (var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.getUserId());
            ps.setDate(2, java.sql.Date.valueOf(d.getEntryDate()));
            ps.setString(3, d.getDiaryContent());
            ps.setString(4, d.getVisibility().name());
            if (d.getSharedDiaryId() == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, d.getSharedDiaryId());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }
}
