package com.share.dairy.api.diary;

import com.share.dairy.dao.diary.DiaryAttachmentDao;
import com.share.dairy.dto.diary.diaryAttachment.CreateRequest;
import com.share.dairy.dto.diary.diaryAttachment.ResponseDto;
import com.share.dairy.service.diary.DiaryAttachmentService;
import com.share.dairy.util.DBConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/diary/{entryId}/attachment")
public class DiaryAttachmentController {

    private final DiaryAttachmentService service;

    public DiaryAttachmentController() {
        this.service = new DiaryAttachmentService(new DiaryAttachmentDao());
    }

    @GetMapping
    public ResponseEntity<List<ResponseDto>> list(@PathVariable long entryId) throws SQLException {
        return ResponseEntity.ok(service.getAttachmentsByEntry(entryId));
    }

    @PostMapping
    public ResponseEntity<Long> create(@PathVariable long entryId,
                                       @RequestBody CreateRequest req) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            long id = service.addAttachment(con, entryId, req);
            return ResponseEntity.ok(id);
        }
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable long attachmentId) throws SQLException {
        try (var con = DBConnection.getConnection()) {
            boolean deleted = service.deleteAttachment(con, attachmentId);
            return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        }
    }
}
