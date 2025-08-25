package com.share.dairy.service.imageGen;

import com.share.dairy.dto.imageGen.ImageGenerateDtos;
import com.share.dairy.repo.imageGen.ImageDbRepository;
import com.share.dairy.util.CharacterAssetResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/**
<<<<<<< HEAD
 * 오케스트레이션 서비스:
 *  1) entryId로 DB에서 (analysis_keywords, character_type, analysis_id, user_id) 조회
 *  2) 캐릭터 PNG 경로 해석 (classpath 또는 file 시스템)
 *  3) ImageGenService 호출 → 2장 생성
 *  4) ✅ diary_attachments에는 더 이상 저장하지 않고
 *       keyword_images / character_keyword_images 두 테이블에만 저장
=======
 * DiaryWorkflowService
 * -------------------------------------------------------
 * 역할:
 *   1) entryId 컨텍스트 조회(analysis_keywords, character_type, analysis_id, user_id)
 *   2) 캐릭터 PNG Path 확인
 *   3) ImageGenService 호출하여 이미지 2장 생성
 *   4) keyword_images / character_keyword_images에 URL 기록(UPSERT)
 *
 * 추가:
 *   - ProgressRegistry로 단계별 진행률/상태 기록(프론트 폴링용)
 *   - generateImagesAsync(...) 비동기 메서드: 프론트의 /auto가 호출
 *   - generateFromDb(...) 동기 메서드: 관리/디버그/배치에서 사용
>>>>>>> origin/이민우
 */
@Service
public class DiaryWorkflowService {

    private final ImageDbRepository imageDbRepo;
    private final CharacterAssetResolver assetResolver;
    private final ImageGenService imageGen;
    private final ProgressRegistry progress;

    public DiaryWorkflowService(ImageDbRepository imageDbRepo,
                                CharacterAssetResolver assetResolver,
                                ImageGenService imageGen,
                                ProgressRegistry progress) {
        this.imageDbRepo = imageDbRepo;
        this.assetResolver = assetResolver;
        this.imageGen = imageGen;
        this.progress = progress;
    }

    // --------------------------------------------------------------------
    // 비동기 엔트리(프론트 사용): /images/auto → 여기로 들어옴
    // regenerate/size 파라미터도 반영
    // --------------------------------------------------------------------
    @Async
    public void generateImagesAsync(long entryId, boolean regenerate, String sizeParam) {
        try {
            progress.start(entryId, "작업 시작: 준비 중");

            // 1) 컨텍스트 조회
            progress.update(entryId, 5, "키워드/캐릭터 로딩");
            var ctxOpt = imageDbRepo.findContext(entryId);
            if (ctxOpt.isEmpty()) {
                throw new IllegalStateException("분석 결과가 먼저 필요합니다. entry_id=" + entryId);
            }
            var ctx = ctxOpt.get();
            if (isBlank(ctx.analysisKeywords())) {
                throw new IllegalStateException("analysis_keywords가 비어 있습니다. entry_id=" + entryId);
            }
            if (isBlank(ctx.characterType())) {
                throw new IllegalStateException("users.character_type이 비어 있습니다. entry_id=" + entryId);
            }

            // 2) 캐릭터 PNG
            progress.update(entryId, 10, "캐릭터 에셋 확인");
            Path basePng = assetResolver.resolve(ctx.characterType());
            System.out.println("[Workflow] basePng=" + basePng + ", exists=" + Files.exists(basePng));

            // 3) 파라미터 정리
            progress.update(entryId, 20, "프롬프트 구성");
            String keyword = ctx.analysisKeywords();
            String size = (sizeParam == null || sizeParam.isBlank()) ? "1024" : sizeParam;
            boolean useCache = !regenerate; // regenerate=false → 캐시 사용

            // 4) 이미지1(키워드)
            progress.update(entryId, 40, "이미지1(키워드) 생성 중…");

            // 5) 이미지2(캐릭터)
            progress.update(entryId, 70, "이미지2(캐릭터) 생성 중…");

            var result = imageGen.generateTwoWithBase_NoMask(
                    entryId,
                    keyword,
                    ctx.characterType(),
                    basePng,
                    useCache,
                    size
            );

            // 6) DB 반영
            progress.update(entryId, 90, "DB 저장 중…");
            imageDbRepo.insertKeywordImageIfAbsent(ctx.analysisId(), ctx.userId(), result.keywordUrl());
            imageDbRepo.insertCharacterImageIfAbsent(ctx.analysisId(), ctx.userId(), result.characterUrl());

            // 7) 완료
            progress.done(entryId, "완료");

        } catch (Exception e) {
            progress.error(entryId, "오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------------------
    // 동기 엔트리(관리/디버그): 즉시 결과 반환. 외부 API는 느리므로 트랜잭션 바깥.
    // --------------------------------------------------------------------
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImageGenerateDtos.GenerateResponse generateFromDb(long entryId,
                                                             boolean regenerate,
                                                             String size) {

        var ctxOpt = imageDbRepo.findContext(entryId);
        if (ctxOpt.isEmpty()) {
            throw new IllegalStateException("분석(키워드) 또는 일기/사용자 정보가 부족합니다. entry_id=" + entryId);
        }
        var ctx = ctxOpt.get();

        if (isBlank(ctx.analysisKeywords())) {
            throw new IllegalStateException("analysis_keywords가 비어 있습니다. entry_id=" + entryId);
        }
        if (isBlank(ctx.characterType())) {
            throw new IllegalStateException("users.character_type이 비어 있습니다. entry_id=" + entryId);
        }

        Path basePng = assetResolver.resolve(ctx.characterType());
        System.out.println("[Workflow] basePng=" + basePng + ", exists=" + Files.exists(basePng));

        var res = imageGen.generateTwoWithBase_NoMask(
                entryId,
                ctx.analysisKeywords(),
                ctx.characterType(),
                basePng,
                /*useCache*/ !regenerate,
                (size == null || size.isBlank()) ? "1024" : size
        );

        imageDbRepo.insertKeywordImageIfAbsent(ctx.analysisId(), ctx.userId(), res.keywordUrl());
        imageDbRepo.insertCharacterImageIfAbsent(ctx.analysisId(), ctx.userId(), res.characterUrl());

        return new ImageGenerateDtos.GenerateResponse(res.keywordUrl(), res.characterUrl());
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}

