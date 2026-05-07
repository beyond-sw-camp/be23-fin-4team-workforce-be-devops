package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 결재 양식/정책 변경 이벤트 (RAG 동기화용).
 *
 * 발행 시점:
 *   - ApprovalForm CRUD
 *   - ApprovalLine 설정 변경
 *
 * 구독: ai-service
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncApprovalEvent {

    public static final String TOPIC = "rag.sync.approval";

    private UUID eventId;
    private UUID companyId;
    private String action;
    private String resourceType;      // APPROVAL_FORM / APPROVAL_LINE
    private UUID resourceId;
    private Instant timestamp;
    private String triggeredBy;
}