package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 직원 퇴직 처리 이벤트
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class MemberTerminationEvent {
    private UUID memberId;
    private UUID companyId;
    private LocalDate retireDate;     // 퇴직일
    private LocalDate joinDate;       // 입사일 (퇴직금 계산용)
    private String reason;            // 퇴직 사유
}