package com.share.dairy.api.diary;

import com.share.dairy.dto.diary.diaryComment.CreateRequest;
import com.share.dairy.dto.diary.diaryComment.ResponseDto;
import com.share.dairy.dto.diary.diaryComment.UpdateRequest;
import com.share.dairy.service.diary.DiaryCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/diary/{entryId}/comments")
@Validated
public class DiaryCommentController {

    private final DiaryCommentService service;

    public DiaryCommentController(DiaryCommentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ResponseDto> create(
            @PathVariable @Positive Long entryId,
            @RequestParam @Positive Long userId,
            @RequestBody @Valid CreateRequest req) throws SQLException {

        return ResponseEntity.ok(service.addComment(entryId, userId, req));
    }

    @GetMapping
    public ResponseEntity<List<ResponseDto>> list(
            @PathVariable @Positive Long entryId) throws SQLException {

        return ResponseEntity.ok(service.getCommentsByEntry(entryId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Void> update(
            @PathVariable @Positive Long commentId,
            @RequestBody @Valid UpdateRequest req) throws SQLException {

        return service.updateComment(commentId, req) ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long commentId) throws SQLException {

        return service.deleteComment(commentId) ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

