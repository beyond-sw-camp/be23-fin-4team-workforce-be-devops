package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 출장 결재 이벤트, approval-service -> salary-service
 * 승인 시 contentJson 의 숙박비/식비 합계를 totalAmount 로 계산해서 발행
 * Consumer 가 출장 종료일에 효력 발생하는 MemberAllowance(AUTO) 생성
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class BusinessTripApprovalEvent {

    public static final String TOPIC = "business-trip-approval";

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;
    private UUID approverId;
    private LocalDateTime decidedAt;

    // 출장 기간
    private LocalDate tripStartDate;
    private LocalDate tripEndDate;

    // 출장수당 총액 (양식의 숙박비+식비 합계, 원화 환산 후)
    private Long totalAmount;

    private Action action;

    public enum Action { APPROVE, REJECT }
}
