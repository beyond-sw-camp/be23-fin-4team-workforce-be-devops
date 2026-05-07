package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class CalendarApprovalEvent {

    public static final String TOPIC = "approval-calendar";

    private UUID companyId;
    private UUID memberId;          // 요청자
    private String requesterName;
    private UUID organizationId;    // 요청자 부서
    private String title;           // "홍길동 연차 (오전반차)"
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID requestId;         // 중복 방지용
}
