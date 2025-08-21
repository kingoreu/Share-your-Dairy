package com.share.dairy.controller.diary_analysis;

import com.share.dairy.dao.diary.DiaryAnalysisDao;
import com.share.dairy.model.diary.DiaryAnalysis;
import com.share.dairy.service.diary_analysis.DiaryAnalysisService;

import java.util.Optional;

public class DiaryAnalysisController {

    private final DiaryAnalysisService service = new DiaryAnalysisService();
    private final DiaryAnalysisDao diaryAnalysisDao = new DiaryAnalysisDao();

    /**
     * entryId의 일기를 분석하고, DB에 upsert된 결과를 다시 읽어 반환.
     */
    public DiaryAnalysis analyzeEntry(long entryId) throws Exception {
        service.process(entryId); // GPT 호출 + diary_analysis upsert
        Optional<DiaryAnalysis> found = diaryAnalysisDao.findByEntryId(entryId);
        return found.orElseThrow(() ->
                new IllegalStateException("분석 결과를 찾을 수 없습니다. entry_id=" + entryId));
    }
}
