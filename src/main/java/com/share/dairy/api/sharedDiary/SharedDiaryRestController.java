package com.share.dairy.api.sharedDiary;

import com.share.dairy.dto.diary.diaryEntry.ResponseDto;
import com.share.dairy.dto.sharedDiary.*;
import com.share.dairy.service.diary.DiaryService;
import com.share.dairy.service.sharedDiary.SharedDiaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.share.dairy.service.sharedDiary.SharedDiaryService.toResponse;

@RestController
@RequestMapping("/api/sharedDiary")
public class SharedDiaryRestController {

    private final SharedDiaryService sharedService;
    private final DiaryService diaryService;

    public SharedDiaryRestController(SharedDiaryService SharedService, DiaryService DiaryService) {
        this.sharedService = SharedService;
        this.diaryService = DiaryService;
    }

    // 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<SharedDiaryResponse> get(@PathVariable long id) throws SQLException {
        return sharedService.findById(id)
                .map(s -> ResponseEntity.ok(toResponse(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    // 소유자 기준 목록
    @GetMapping
    public ResponseEntity<List<SharedDiaryResponse>> listByOwner(@RequestParam long ownerId) throws SQLException {
        var list = sharedService.findByOwner(ownerId).stream()
                .map(SharedDiaryService::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    // 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody SharedDiaryCreateRequest req) throws SQLException {
        long id = sharedService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", id, "message", "공유 일기장 생성"));
    }

    // 제목 수정
    @PutMapping("/{id}/title")
    public ResponseEntity<Map<String, String>> updateTitle(
            @PathVariable long id,
            @RequestBody SharedDiaryUpdateRequest req) throws SQLException {
        sharedService.updateTitle(id, req.getSharedDiaryTitle());
        return ResponseEntity.ok(Map.of("message", "제목 수정 완료"));
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable long id) throws SQLException {
        sharedService.delete(id);
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }

    // 공유 일기장 기준 글 목록
    @GetMapping("/{sharedDiaryId}/posts")
    public ResponseEntity<List<ResponseDto>> listPosts(@PathVariable long sharedDiaryId) throws SQLException {
        var list = diaryService.findAllBySharedDiaryId(sharedDiaryId)
                .stream().map(DiaryService::toResponse).toList();
        return ResponseEntity.ok(list);
    }
}
