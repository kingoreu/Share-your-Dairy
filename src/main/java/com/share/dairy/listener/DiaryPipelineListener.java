package com.share.dairy.listener;

import com.share.dairy.dto.imageGen.ImageGenerateDtos;
import com.share.dairy.event.DiaryAnalyzedEvent;
import com.share.dairy.service.imageGen.DiaryWorkflowService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 분석이 끝났다는 이벤트를 받으면, 사용자 액션 없이
 * 키워드 일러스트 + 캐릭터 액션 이미지 2장을 자동 생성한다.
 *
 * 주의) 현재 분석 저장은 직접 JDBC로 커밋되므로 @EventListener만 써도 충분하다.
 *      (스프링 트랜잭션 경계에 묶이지 않음)
 */
@Component
public class DiaryPipelineListener {

    private final DiaryWorkflowService workflow;

    public DiaryPipelineListener(DiaryWorkflowService workflow) {
        this.workflow = workflow;
    }

    @EventListener
    public void onAnalyzed(DiaryAnalyzedEvent ev) {
        try {
            // regenerate=false → 이미 파일 있으면 캐시 사용, 기본 크기 1024
            ImageGenerateDtos.GenerateResponse res =
                    workflow.generateFromDb(ev.entryId(), /*regenerate*/ false, "1024");
            System.out.println("[Auto] images created: "
                    + res.keywordImageUrl + " / " + res.characterImageUrl);
        } catch (Exception e) {
            // 이미지 실패가 전체 저장/분석을 망치지 않도록 로깅만
            System.err.println("[Auto] image generation failed for entry "
                    + ev.entryId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
