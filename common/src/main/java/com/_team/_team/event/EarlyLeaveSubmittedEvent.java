package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 조퇴계 결재 상신 이벤트
 * 일일근태를 UNDER_REVIEW 로 격리
 * 결재 진행 동안 같은 날에 정정/연장/조퇴 등 다른 결재가 들어오지 못하게 보호
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class EarlyLeaveSubmittedEvent {

    public static final String TOPIC = "early-leave-submitted";

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;

    // 조퇴 대상 일자 (오늘 기준 7일 이내)
    private LocalDate attendanceDate;

    // 조퇴 시각 (당일 시각, HH:mm)
    private LocalDateTime earlyLeaveAt;

    // 조퇴 사유
    private String reason;

    // 신청자
    private UUID submittedBy;

    // 신청 시각
    private LocalDateTime submittedAt;
}
