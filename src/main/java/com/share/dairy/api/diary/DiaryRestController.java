// src/main/java/com/share/dairy/api/diary/DiaryRestController.java
package com.share.dairy.api.diary;

import com.share.dairy.dto.diary.diaryEntry.CreateRequest;
import com.share.dairy.dto.diary.diaryEntry.ResponseDto;
import com.share.dairy.dto.diary.diaryEntry.ShareRequest;
import com.share.dairy.dto.diary.diaryEntry.UpdateRequest;
import com.share.dairy.service.diary.DiaryService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import static com.share.dairy.service.diary.DiaryService.toResponse;

@RestController
@RequestMapping("/api/diary")
public class DiaryRestController {

    private final DiaryService service;

    public DiaryRestController(DiaryService service) {
        this.service = service;
    }

    // 사용자별 목록
    @GetMapping
    public ResponseEntity<List<ResponseDto>> listByUser(@RequestParam long userId) throws SQLException {
        var list = service.findAllByUser(userId).stream().map(DiaryService::toResponse).toList();
        return ResponseEntity.ok(list);
    }

    // 단건 조회
    @GetMapping("/{entryId}")
    public ResponseEntity<ResponseDto> get(@PathVariable long entryId) throws SQLException {
        return service.findById(entryId).map(e -> ResponseEntity.ok(toResponse(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    // 생성 (개인/공유 동시 가능: sharedDiaryId 있으면 바로 연결)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest req) throws SQLException {
        long id = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // 내용 수정
    @PutMapping("/{entryId}/content")
    public ResponseEntity<?> updateContent(@PathVariable long entryId,
                                           @RequestBody UpdateRequest req) throws SQLException {
        service.updateContent(entryId, req.getDiaryContent());
        return ResponseEntity.ok().build();
    }

    // 삭제
    @DeleteMapping("/{entryId}")
    public ResponseEntity<?> delete(@PathVariable long entryId) throws SQLException {
        service.delete(entryId);
        return ResponseEntity.ok().build();
    }

    // 기존 일기 공유 시작 (공유 일기장에 연결)
    @PutMapping("/{entryId}/share")
    public ResponseEntity<?> share(@PathVariable long entryId,
                                   @RequestBody ShareRequest req) throws SQLException {
        service.share(entryId, req.getSharedDiaryId());
        return ResponseEntity.ok().build();
    }

    // 공유 해제 (연결 제거)
    @DeleteMapping("/{entryId}/share")
    public ResponseEntity<?> unshare(@PathVariable long entryId) throws SQLException {
        service.unshare(entryId);
        return ResponseEntity.ok().build();
    }

    // 공유 추가(기존 글을 공유 일기장에 연결)
    // 내 생각에 이거는 삭제해도 될 듯
    @PostMapping("/shared/{sharedDiaryId}/posts/{entryId}")
    public ResponseEntity<?> addToShared(@PathVariable long sharedDiaryId, @PathVariable long entryId) throws SQLException {
        service.share(entryId, sharedDiaryId);
        return ResponseEntity.ok().build();
    }

    // 공유 제거(매핑만 제거)
    @DeleteMapping("/shared/{sharedDiaryId}/posts/{entryId}")
    public ResponseEntity<?> removeFromShared(@PathVariable long sharedDiaryId, @PathVariable long entryId) throws SQLException {
        service.unshare(entryId);
        return ResponseEntity.ok().build();
    }
}