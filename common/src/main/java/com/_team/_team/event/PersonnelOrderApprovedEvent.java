package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 인사발령 결재 승인 이벤트
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class PersonnelOrderApprovedEvent {
    public static final String TOPIC = "personnel-order-approved";

    private UUID companyId;
    private UUID approvalDocumentId;     // 결재 문서 ID (이력 추적용)
    private UUID approverId;
    private LocalDateTime decidedAt;
    private LocalDate effectiveDate;     // 발령 효력 시작일
    private List<Item> items;            // 발령 대상 직원 목록

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Getter
    public static class Item {
        private UUID memberId;
        private String memberName;       // 메시지/이력 표시용
        /** TRANSFER / PROMOTION / DEMOTION / REASSIGN / ROLE_CHANGE */
        private String orderType;
        /** 부서 이동 - 없으면 null */
        private UUID beforeOrganizationId;
        private UUID afterOrganizationId;
        private String beforeOrganizationName;
        private String afterOrganizationName;
        /** 직급 변경 - 없으면 null */
        private String beforeJobGradeName;
        private String afterJobGradeName;
        /** 직책 변경 - 없으면 null */
        private String beforeJobTitleName;
        private String afterJobTitleName;
        /** 호봉 변경(호봉제만) - 없으면 null */
        private Integer beforeStep;
        private Integer afterStep;
        private String reason;
    }
}
