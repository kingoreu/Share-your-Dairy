// src/main/java/com/share/dairy/service/imageGen/DiaryWorkflowService.java
package com.share.dairy.service.imageGen;

import com.share.dairy.dto.imageGen.ImageGenerateDtos;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import com.share.dairy.util.CharacterAssetResolver;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 오케스트레이션 서비스:
 *  1) entryId로 DB에서 (analysis_keywords, character_type, analysis_id, user_id) 조회
 *  2) 캐릭터 PNG 경로 해석 (classpath 또는 file 시스템)
 *  3) ImageGenService 호출 → 2장 생성
 *  4) DB에 첨부/생성기록 반영
 */
@Service
public class DiaryWorkflowService {

    private final ImageDbRepository imageDbRepo;
    private final CharacterAssetResolver assetResolver;
    private final ImageGenService imageGen;

    public DiaryWorkflowService(ImageDbRepository imageDbRepo,
                                CharacterAssetResolver assetResolver,
                                ImageGenService imageGen) {
        this.imageDbRepo = imageDbRepo;
        this.assetResolver = assetResolver;
        this.imageGen = imageGen;
    }

    /** regenerate=false 면 파일이 이미 있으면 캐시 사용(재생성 생략) */
    public ImageGenerateDtos.GenerateResponse generateFromDb(long entryId,
                                                             boolean regenerate,
                                                             String size) {
        // 1) 컨텍스트 조회 (4필드)
        var ctxOpt = imageDbRepo.findContext(entryId);
        if (ctxOpt.isEmpty()) {
            throw new IllegalStateException("분석(키워드) 또는 일기/사용자 정보가 부족합니다. entry_id=" + entryId);
        }
        var ctx = ctxOpt.get();

        if (ctx.analysisKeywords() == null || ctx.analysisKeywords().isBlank()) {
            throw new IllegalStateException("analysis_keywords가 비어 있습니다. entry_id=" + entryId);
        }
        if (ctx.characterType() == null || ctx.characterType().isBlank()) {
            throw new IllegalStateException("users.character_type이 비어 있습니다. entry_id=" + entryId);
        }

        // 2) 캐릭터 PNG 파일 찾기
        Path basePng = assetResolver.resolve(ctx.characterType());
        System.out.println("[Workflow] basePng=" + basePng + ", exists=" + Files.exists(basePng));

        // 3) 이미지 2장 생성 (키워드/캐릭터 라벨을 프롬프트에 반영)
        var res = imageGen.generateTwoWithBase_NoMask(
                entryId,
                ctx.analysisKeywords(),
                ctx.characterType(),
                basePng,
                /*useCache*/ !regenerate,
                size
        );

        // 4) 컨트롤러/리스너로 응답 DTO
        return new ImageGenerateDtos.GenerateResponse(res.keywordUrl(), res.characterUrl());
    }
}
