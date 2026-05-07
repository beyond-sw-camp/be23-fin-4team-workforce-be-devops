package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ResignationApprovalEvent {
    public static final String TOPIC = "resignation-approval";

    public enum Action { APPROVE, REJECT, CANCEL }

    private UUID companyId;
    private UUID memberId;
    private UUID requestId;
    private LocalDate resignDate;       // 사직서의 퇴직 희망일
    private String resignReason;        // 일신상의 사유 등
    private String detail;              // 상세 사유
    private UUID approverId;
    private LocalDateTime decidedAt;
    private String note;                // 반려 코멘트
    private Action action;
}