// src/main/java/com/share/dairy/api/ImageController.java
package com.share.dairy.api.imageGen;

import com.share.dairy.dto.imageGen.ImageGenerateDtos.GenerateRequest;
import com.share.dairy.dto.imageGen.ImageGenerateDtos.GenerateResponse;
import com.share.dairy.service.imageGen.DiaryWorkflowService;
import com.share.dairy.service.imageGen.ImageGenService;
import com.share.dairy.util.CharacterAssetResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/**
 * 이미지 생성 API
 * - 자동 파이프라인이 기본이지만, 재생성/디버깅을 위해 수동 엔드포인트도 제공
 */
@RestController
@RequestMapping("/api")
public class ImageController {

    private final ImageGenService imageSvc;
    private final CharacterAssetResolver assetResolver;
    private final DiaryWorkflowService workflow;

    public ImageController(ImageGenService imageSvc,
                           CharacterAssetResolver assetResolver,
                           DiaryWorkflowService workflow) {
        this.imageSvc = imageSvc;
        this.assetResolver = assetResolver;
        this.workflow = workflow;
    }

    /** (신규) DB에서 키워드/캐릭터 읽어 자동 생성: entryId만 필요 */
    @PostMapping("/diary/{entryId}/images/auto")
    public ResponseEntity<?> autoGenerate(@PathVariable long entryId,
                                          @RequestParam(defaultValue = "false") boolean regenerate,
                                          @RequestParam(defaultValue = "1024") String size) {
        try {
            var res = workflow.generateFromDb(entryId, regenerate, size);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body(Map.of(
                    "error","auto generation failed",
                    "detail", e.getMessage()
            ));
        }
    }

    /** (기존) 수동 생성: 클라가 keyword/character를 직접 보냄(재생성/디버깅용) */
    @PostMapping("/diary/{entryId}/images")
    public ResponseEntity<?> generateTwo(@PathVariable long entryId,
                                         @RequestBody GenerateRequest req) {
        if (req == null || req.keyword == null || req.keyword.isBlank()
                || req.character == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error","missing fields", "need","keyword & character"
            ));
        }
        try {
            // 추가 (enum 에서 파일 경로 추출)
            String charPath = req.character.getImagePath();

            Path baseCharPng = assetResolver.resolve(charPath);
            var res = imageSvc.generateTwoWithBase_NoMask(
                    entryId, req.keyword, req.character.name(), baseCharPng,
                    !(Boolean.TRUE.equals(req.regenerate)), req.size
            );
            return ResponseEntity.ok(new GenerateResponse(res.keywordUrl(), res.characterUrl()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error","image generation failed", "detail", e.getMessage()
            ));
        }
    }
}
