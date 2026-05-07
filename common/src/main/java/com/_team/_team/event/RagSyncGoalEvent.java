package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 목표/평가 정책 변경 이벤트 (RAG 동기화용).
 *
 * 발행 시점:
 *   - KpiTemplate CRUD
 *   - EvaluationSeason 생명주기 변경
 *   - EvaluationDesign CRUD
 *
 * 구독: ai-service
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncGoalEvent {

    public static final String TOPIC = "rag.sync.goal";

    private UUID eventId;
    private UUID companyId;
    private String action;
    private String resourceType;      // KPI_TEMPLATE / EVALUATION_SEASON / EVALUATION_DESIGN
    private UUID resourceId;
    private Instant timestamp;
    private String triggeredBy;
}