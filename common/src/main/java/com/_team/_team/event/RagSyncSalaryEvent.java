package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 급여 정책 변경 이벤트 (RAG 동기화용).
 *
 * 발행 시점:
 *   - SalaryItemTemplate CRUD
 *   - SalaryPolicy CRUD
 *   - TaxRate 변경
 *
 * 구독: ai-service
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncSalaryEvent {

    public static final String TOPIC = "rag.sync.salary";

    private UUID eventId;
    private UUID companyId;
    private String action;            // CREATED / UPDATED / DELETED / BULK
    private String resourceType;      // SALARY_ITEM_TEMPLATE / SALARY_POLICY / TAX_RATE
    private UUID resourceId;
    private Instant timestamp;
    private String triggeredBy;
}