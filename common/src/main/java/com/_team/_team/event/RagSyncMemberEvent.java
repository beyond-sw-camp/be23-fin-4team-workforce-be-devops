package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 회사/권한 정책 변경 이벤트 (RAG 동기화용).
 *
 * 발행 시점:
 *   - Role CRUD (권한 설정 변경)
 *   - Organization 구조 변경 (대규모)
 *   - EsgConfig 변경
 *
 * 구독: ai-service
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncMemberEvent {

    public static final String TOPIC = "rag.sync.member";

    private UUID eventId;
    private UUID companyId;
    private String action;
    private String resourceType;      // ROLE / ORGANIZATION / ESG_CONFIG
    private UUID resourceId;
    private Instant timestamp;
    private String triggeredBy;
}