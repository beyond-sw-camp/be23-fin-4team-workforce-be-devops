package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncLeaveEvent {

    public static final String TOPIC = "rag.sync.leave";

    private UUID eventId;
    private UUID companyId;
    private String action;          // CREATED / UPDATED / DELETED / BULK
    private String resourceType;    // COMPANY_LEAVE_TYPE / LEAVE_POLICY
    private UUID resourceId;        // 선택
    private Instant timestamp;
    private String triggeredBy;     // memberId 또는 "system"
}