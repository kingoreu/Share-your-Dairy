package com.share.dairy.service.diary;

import com.share.dairy.dao.diary.DiaryCommentDao;
import com.share.dairy.dto.diary.diaryComment.CreateRequest;
import com.share.dairy.dto.diary.diaryComment.ResponseDto;
import com.share.dairy.dto.diary.diaryComment.UpdateRequest;
import com.share.dairy.model.diary.DiaryComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryCommentService {

    // 일단 Transactional 써봤는데 ㅂㄹ면 안쓸거
    private final DiaryCommentDao dao;

    // 댓글 작성
    @Transactional
    public ResponseDto addComment(Long entryId, Long userId, CreateRequest req) throws SQLException {
        // (옵션) parentId가 있으면 존재/동일 entryId 확인
        if (req.getParentId() != null) {
            dao.findById(req.getParentId()).ifPresentOrElse(parent -> {
                if (!parent.getEntryId().equals(entryId)) {
                    throw new IllegalArgumentException("부모 댓글의 entryId가 일치하지 않습니다.");
                }
            }, () -> {
                throw new IllegalArgumentException("부모 댓글을 찾을 수 없습니다.");
            });
        }

        DiaryComment c = new DiaryComment();
        c.setEntryId(entryId);
        c.setUserId(userId);
        c.setParentId(req.getParentId());
        c.setCommentContent(req.getCommentContent());
        c.setDeleted(false);

        dao.insert(c); // 생성 시 generated key 채워짐
        // insert 시 created_at은 DB NOW() 사용, 필요하면 select로 재조회 가능
        return toDto(c);
    }

    // entryId 기준 목록
    @Transactional(readOnly = true)
    public List<ResponseDto> getCommentsByEntry(Long entryId) throws SQLException {
        return dao.findAllByEntryId(entryId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // 댓글 수정 (내용만)
    @Transactional
    public boolean updateComment(Long commentId, UpdateRequest req) throws SQLException {
        return dao.findById(commentId)
                .map(c -> {
                    c.setCommentContent(req.getCommentContent());
                    c.setDeleted(false); // 수정 시 삭제 상태 해제하지 않으려면 제거
                    try {
                        return dao.update(c) > 0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(false);
    }

    // 댓글 삭제
    @Transactional
    public boolean deleteComment(Long commentId) throws SQLException {
        return dao.delete(commentId) > 0;
    }


    private ResponseDto toDto(DiaryComment c) {
        var d = new ResponseDto();
        d.setCommentId(c.getCommentId());
        d.setEntryId(c.getEntryId());
        d.setUserId(c.getUserId());
        d.setParentId(c.getParentId());
        d.setCommentContent(c.getCommentContent());
        d.setDeleted(c.isDeleted());
        d.setCreatedAt(c.getCreatedAt());
        d.setUpdatedAt(c.getUpdatedAt());
        return d;
    }
}
