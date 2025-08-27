package com.share.dairy.event;

/**
 * diary_analysis 테이블에 분석 결과가 정상 반영된 "뒤"에
 * 후속 파이프라인(이미지 생성 등)을 돌리기 위한 이벤트.
 * 데이터는 entryId 하나면 충분하다.
 */
public record DiaryAnalyzedEvent(long entryId) {}