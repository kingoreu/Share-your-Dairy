// src/main/java/com/share/dairy/service/imageGen/DiaryWorkflowService.java
package com.share.dairy.service.imageGen;

import com.share.dairy.dto.imageGen.ImageGenerateDtos;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import com.share.dairy.util.CharacterAssetResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 오케스트레이션 서비스:
 *  1) entryId로 DB에서 (analysis_keywords, character_type, analysis_id, user_id) 조회
 *  2) 캐릭터 PNG 경로 해석 (classpath 또는 file 시스템)
 *  3) ImageGenService 호출 → 2장 생성
 *  4) ✅ diary_attachments에는 더 이상 저장하지 않고
 *       keyword_images / character_keyword_images 두 테이블에만 저장
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

    /**
     * regenerate=false 면 파일이 이미 있으면 캐시 사용(재생성 생략)
     *
     * ⚠️ 외부 API 호출(이미지 생성)은 느릴 수 있으므로
     *    DB 트랜잭션을 열지 않도록 NOT_SUPPORTED로 실행.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // ✅ 변경: 트랜잭션 밖에서 실행
    public ImageGenerateDtos.GenerateResponse generateFromDb(long entryId,
                                                             boolean regenerate,
                                                             String size) {
        // 1) 컨텍스트 조회 (analysisId, userId, analysisKeywords, characterType)
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

        // 4) ✅ DB 반영 정책 변경
        //    - diary_attachments: 더 이상 쓰지 않음
        //    - keyword_images / character_keyword_images: 경로(path_or_url) 저장 (UPSERT)
        //      => 테이블에 UNIQUE(analysis_id, user_id)가 걸려 있어야 최적
        imageDbRepo.insertKeywordImageIfAbsent(ctx.analysisId(), ctx.userId(), res.keywordUrl());    // ✅ 추가
        imageDbRepo.insertCharacterImageIfAbsent(ctx.analysisId(), ctx.userId(), res.characterUrl()); // ✅ 추가

        // 컨트롤러/리스너로 응답 DTO
        return new ImageGenerateDtos.GenerateResponse(res.keywordUrl(), res.characterUrl());
    }
}
