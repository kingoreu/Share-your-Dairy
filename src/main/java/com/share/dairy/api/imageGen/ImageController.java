package com.share.dairy.api.imageGen;

import com.share.dairy.dto.imageGen.ImageGenerateDtos;
import com.share.dairy.service.imageGen.DiaryWorkflowService;
import com.share.dairy.dto.imageGen.ImageJobStatusDto;
import com.share.dairy.service.imageGen.ProgressRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 이미지 생성/상태 컨트롤러 (통합본)
 *
 * 클라이언트(JavaFX)가 사용하는 엔드포인트:
 *  - POST /api/diary/{entryId}/images/auto      ← 비동기 시작(202)
 *  - GET  /api/diary/{entryId}/images/status    ← 진행률 폴링
 *
 * 관리/디버그용(동기):
 *  - POST /api/diary/{entryId}/images/auto/sync?regenerate=false&size=1024
 */
@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class ImageController {

    private final DiaryWorkflowService workflow;
    private final ProgressRegistry progress; // 진행률 저장/조회

    /** 비동기 생성 시작: 202 Accepted 즉시 반환 */
    @PostMapping("/{entryId}/images/auto")
    public ResponseEntity<Void> autoGenerate(
            @PathVariable long entryId,
            @RequestParam(name = "regenerate", defaultValue = "false") boolean regenerate,
            @RequestParam(name = "size", required = false) String size
    ) {
        // 1) 상태 레지스트리에 작업 등록
        progress.start(entryId, "대기열 등록");

        // 2) 비동기 워크플로우 시작(선택 파라미터 반영)
        workflow.generateImagesAsync(entryId, regenerate, size);

        // 3) 즉시 202 반환 (프론트는 /status를 폴링)
        return ResponseEntity.accepted().build();
    }

    /** 상태 조회: {status, progress, message} */
    @GetMapping("/{entryId}/images/status")
    public ResponseEntity<?> status(@PathVariable long entryId) {
        var st = progress.get(entryId);
        if (st == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No job for entryId=" + entryId);
        }
        return ResponseEntity.ok(ImageJobStatusDto.of(st));
    }

    /** (옵션) 동기 생성: 관리/디버그용 — 프론트에서는 미사용 권장 */
    @PostMapping("/{entryId}/images/auto/sync")
    public ResponseEntity<ImageGenerateDtos.GenerateResponse> autoGenerateSync(
            @PathVariable long entryId,
            @RequestParam(name = "regenerate", defaultValue = "false") boolean regenerate,
            @RequestParam(name = "size", defaultValue = "1024") String size
    ) {
        var res = workflow.generateFromDb(entryId, regenerate, size);
        return ResponseEntity.ok(res);
    }
}
