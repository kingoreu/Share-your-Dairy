package com.share.dairy.service.diary;

import com.share.dairy.dao.diary.DiaryAttachmentDao;
import com.share.dairy.dto.diary.diaryAttachment.CreateRequest;
import com.share.dairy.dto.diary.diaryAttachment.ResponseDto;
import com.share.dairy.model.diary.DiaryAttachment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class DiaryAttachmentService {

    private final DiaryAttachmentDao dao;

    public DiaryAttachmentService(DiaryAttachmentDao dao) {
        this.dao = dao;
    }

    public List<ResponseDto> getAttachmentsByEntry(long entryId) throws SQLException {
        return dao.findByEntry(entryId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public long addAttachment(Connection con, long entryId, CreateRequest req) throws SQLException {
        DiaryAttachment a = new DiaryAttachment();
        a.setEntryId(entryId);
        a.setAttachmentType(req.getAttachmentType());
        a.setPathOrUrl(req.getPathOrUrl());
        a.setDisplayOrder(req.getDisplayOrder());
        return dao.insert(con, a);
    }

    public boolean deleteAttachment(Connection con, long attachmentId) throws SQLException {
        return dao.deleteById(con, attachmentId) > 0;
    }

    private ResponseDto toDto(DiaryAttachment a) {
        ResponseDto dto = new ResponseDto();
        dto.setAttachmentId(a.getAttachmentId());
        dto.setEntryId(a.getEntryId());
        dto.setAttachmentType(a.getAttachmentType());
        dto.setPathOrUrl(a.getPathOrUrl());
        dto.setDisplayOrder(a.getDisplayOrder());
        dto.setAttachmentCreatedAt(a.getAttachmentCreatedAt());
        return dto;
    }
}
