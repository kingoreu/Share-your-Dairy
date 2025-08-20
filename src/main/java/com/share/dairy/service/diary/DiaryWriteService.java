package com.share.dairy.service.diary;

import com.share.dairy.dao.diary.DiaryAttachmentDao;
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.model.diary.DiaryAttachment;
import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.util.Tx;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 트랜잭션 기반 다이어리 작성 서비스.
 * - 본문과 첨부를 하나의 트랜잭션으로 처리
 * - DB 트리거가 diary_updated_at 갱신을 담당하므로 여기선 값 세팅하지 않음
 */
public class DiaryWriteService {

    private final DiaryEntryDao diaryEntryDao = new DiaryEntryDao();
    private final DiaryAttachmentDao diaryAttachmentDao = new DiaryAttachmentDao();

    /**
     * 본문 + 첨부 N개 저장 (모두 성공해야 commit)
     * @return 생성된 entry_id
     */
    public long createWithAttachments(DiaryEntry entry, List<DiaryAttachment> attachments) throws SQLException {
        normalize(entry); // ← null-safe 기본값 세팅
        return Tx.inTx(con -> {
            long entryId = insertEntry(con, entry); // 본문 저장
            if (attachments != null && !attachments.isEmpty()) {
                for (DiaryAttachment a : attachments) {
                    if (a == null) continue;
                    a.setEntryId(entryId);
                    diaryAttachmentDao.insert(con, a); // 첨부 저장
                }
            }
            return entryId; // commit
        });
    }

    /** 내 일기 목록 로드 */
    public List<DiaryEntry> loadMyDiaryList(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 가 null 입니다.");
        }
        try {
            return diaryEntryDao.findAllByUser(userId);
        } catch (SQLException e) {
            // 예) Unknown column 'title' in 'field list' 같은 원인 메시지 그대로 노출
            throw new RuntimeException("SQL 실패: " + e.getMessage(), e);
        }
    }

    /** 첨부 없이 본문만 저장 (트랜잭션 포함) */
    public long create(DiaryEntry entry) throws SQLException {
        normalize(entry);
        return Tx.inTx(con -> insertEntry(con, entry));
    }

    /**
     * 같은 Connection으로 INSERT 수행 (트랜잭션 안에서 호출)
     * - visibility, sharedDiaryId 는 null 허용
     * - entryDate 가 null이면 오늘 날짜로 저장
     */
    private long insertEntry(Connection con, DiaryEntry d) throws SQLException {
        String sql = """
            INSERT INTO diary_entries
              (user_id, entry_date, title, diary_content, visibility, shared_diary_id)
            VALUES (?,?,?,?,?,?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (d.getUserId() == null) {
                throw new SQLException("user_id is null (DiaryEntry.userId 필수)");
            }
            LocalDate date = (d.getEntryDate() != null) ? d.getEntryDate() : LocalDate.now();

            ps.setLong(1, d.getUserId());
            ps.setObject(2, date);
            ps.setString(3, d.getTitle());                // ★ title (빈문자열 가능)
            ps.setString(4, d.getDiaryContent());         // ★ content (빈문자열 가능)
            ps.setString(5, d.getVisibility() == null ? "PRIVATE" : d.getVisibility().name());
            if (d.getSharedDiaryId() == null) ps.setNull(6, Types.BIGINT);
            else ps.setLong(6, d.getSharedDiaryId());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    /** 일기 삭제 (첨부 → 본문 순서, 트랜잭션) */
    public void deleteEntry(long entryId) throws SQLException {
        Tx.inTx(con -> {
            // 1) 첨부 먼저 시도 (없어도 통과)
            try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM diary_attachments WHERE entry_id=?"
            )) {
                ps.setLong(1, entryId);
                ps.executeUpdate();
            } catch (SQLException ignore) {
                // 첨부 테이블 없거나 FK CASCADE인 경우 무시
            }

            // 2) 본문 삭제
            try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM diary_entries WHERE entry_id=?"
            )) {
                ps.setLong(1, entryId);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("삭제 대상 일기가 존재하지 않습니다. entry_id=" + entryId);
                }
            }
            return null; // commit
        });
    }

    /** 본문만 업데이트 (트랜잭션) */
    public void updateContent(long entryId, String content) throws SQLException {
        Tx.inTx(con -> {
            diaryEntryDao.updateContent(con, entryId, content == null ? "" : content);
            return null; // commit
        });
    }

    /** (옵션) 제목/내용 동시 업데이트가 필요할 때 사용 */
    public void updateTitleAndContent(long entryId, String title, String content) throws SQLException {
        Tx.inTx(con -> {
            // content는 기존 구현 그대로 사용
            diaryEntryDao.updateContent(con, entryId, content == null ? "" : content);
            // 제목 업데이트가 필요한 DAO 메서드가 있다면 호출하도록 확장(없으면 생략 가능)
            // 예: diaryEntryDao.updateTitle(con, entryId, title == null ? "" : title.trim());
            return null;
        });
    }

    /** 저장 전에 null 에 기본값 채워주는 보정 */
    private static void normalize(DiaryEntry d) {
        if (d.getEntryDate() == null) d.setEntryDate(LocalDate.now());
        if (d.getVisibility() == null) d.setVisibility(Visibility.PRIVATE);
        if (d.getTitle() == null) d.setTitle("");
        if (d.getDiaryContent() == null) d.setDiaryContent("");
        // sharedDiaryId 는 null 허용
    }
}
