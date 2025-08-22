package com.share.dairy.controller.diary_analysis;

import com.share.dairy.dao.diary.DiaryAnalysisDao;
import com.share.dairy.event.DiaryAnalyzedEvent;          // ✅ 이벤트 import
import com.share.dairy.model.diary.DiaryAnalysis;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;
import org.springframework.context.ApplicationEventPublisher; // ✅ 퍼블리셔 import
import org.springframework.stereotype.Service;                // ✅ 스프링 빈 어노테이션

import java.util.Optional;

/**
 * ✅ 핵심 포인트
 * - @Service 로 스프링이 관리하는 빈으로 등록
 * - ApplicationEventPublisher 를 생성자 주입
 * - 분석 성공 후 publisher.publishEvent(new DiaryAnalyzedEvent(entryId));
 *   를 호출해 자동 이미지 생성 파이프라인을 트리거
 */
@Service
public class DiaryAnalysisController {

    // 기존과 동일: 외부 API + 직접 JDBC 사용하는 독립 클래스
    private final DiaryAnalysisService service = new DiaryAnalysisService();
    private final DiaryAnalysisDao diaryAnalysisDao = new DiaryAnalysisDao();

    // ✅ 스프링이 주입해 주는 이벤트 퍼블리셔
    private final ApplicationEventPublisher publisher;

    public DiaryAnalysisController(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * entryId의 일기를 분석하고, DB에 upsert된 결과를 다시 읽어 반환.
     * 분석 결과가 정상 저장되면 "분석 완료" 이벤트를 발행하여
     * 이미지 2장 자동 생성 리스너를 호출한다.
     */
    public DiaryAnalysis analyzeEntry(long entryId) throws Exception {
        // 1) GPT 호출 + diary_analysis upsert (직접 JDBC로 커밋됨)
        service.process(entryId);

        // 2) 방금 저장된 결과 검증
        Optional<DiaryAnalysis> found = diaryAnalysisDao.findByEntryId(entryId);
        DiaryAnalysis result = found.orElseThrow(() ->
                new IllegalStateException("분석 결과를 찾을 수 없습니다. entry_id=" + entryId));

        // 3) ✅ 이벤트 발행 → 리스너가 / 이미지 2장 자동 생성
        publisher.publishEvent(new DiaryAnalyzedEvent(entryId));

        return result;
    }
}