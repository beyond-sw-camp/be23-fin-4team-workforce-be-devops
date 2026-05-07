package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 근무/근태 정책 변경 이벤트 (RAG 동기화용).
 *
 * 발행 시점:
 *   - WorkSchedule CRUD
 *   - OvertimePolicy CRUD
 *   - CompanyHoliday CRUD
 *   - FlexibleTimeSlot CRUD
 *
 * 구독: ai-service
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagSyncAttendanceEvent {

    public static final String TOPIC = "rag.sync.attendance";

    private UUID eventId;
    private UUID companyId;
    private String action;
    private String resourceType;      // WORK_SCHEDULE / OVERTIME_POLICY / HOLIDAY / TIME_SLOT
    private UUID resourceId;
    private Instant timestamp;
    private String triggeredBy;
}