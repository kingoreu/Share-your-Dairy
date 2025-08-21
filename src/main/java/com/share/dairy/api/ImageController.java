package com.share.dairy.api;

import com.share.dairy.dto.imageGen.ImageGenerateDtos.GenerateRequest;
import com.share.dairy.dto.imageGen.ImageGenerateDtos.GenerateResponse;
import com.share.dairy.service.imageGen.ImageGenService;
import com.share.dairy.util.CharacterAssetResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/**
 * POST /api/diary/{entryId}/images
 * Body: {"keyword":"야구","character":"raccoon","regenerate":false,"size":"1024"}
 * Resp: {"keywordImageUrl":"/media/12345_keyword.png","characterImageUrl":"/media/12345_character.png"}
 */
@RestController
@RequestMapping("/api")
public class ImageController {

    private final ImageGenService imageSvc;
    private final CharacterAssetResolver assetResolver;

    public ImageController(ImageGenService imageSvc, CharacterAssetResolver assetResolver) {
        this.imageSvc = imageSvc;
        this.assetResolver = assetResolver;
    }

    @PostMapping("/diary/{entryId}/images")
    public ResponseEntity<?> generateTwo(@PathVariable long entryId,
                                         @RequestBody GenerateRequest req) {
        // 1) 입력 검증
        // 수정 버전
        if (req == null || req.keyword == null || req.keyword.isBlank()
                || req.character == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing fields", "need", "keyword & character"
            ));
        }
//        if (req == null || req.keyword == null || req.keyword.isBlank()
//                || req.character == null || req.character.isBlank()) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "error","missing fields", "need","keyword & character"
//            ));
//        }
        // 2) 캐릭터 PNG 확인
        final Path baseCharPng;

        try {
            baseCharPng = assetResolver.resolve(req.character.name().toLowerCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error","character file not found", "detail", e.getMessage()
            ));
        }
        // 3) 생성 실행 (수정: 2단계를 합침~)
        try {

            var res = imageSvc.generateTwoWithBase_NoMask(
                    entryId, req.keyword, baseCharPng,
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
