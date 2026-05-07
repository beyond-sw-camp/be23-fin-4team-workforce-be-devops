package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.enums.BundleApprovalKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GoalApprovalBundleSummaryResDto {
    private UUID requestId;
    private String status;
    /** 번들이 활성화(ACTIVATION) 승인인지 종료(COMPLETION) 승인인지 구분. */
    private BundleApprovalKind approvalKind;
    private int goalCount;
    private LocalDateTime requestedAt;
    private String completionSummary;
    private String completionEvidenceFiles;
}
