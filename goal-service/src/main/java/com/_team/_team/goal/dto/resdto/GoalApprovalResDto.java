package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.enums.BundleApprovalKind;
import com._team._team.goal.domain.enums.GoalApprovalStatus;
import com._team._team.goal.domain.enums.GoalApprovalDecision;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GoalApprovalResDto {
    private UUID goalId;
    /** 승인 번들 ID — 명세의 approvalRequestId / 프론트 requestId */
    private UUID requestId;
    private GoalApprovalStatus approvalStatus;
    /** 번들이 활성화(ACTIVATION) 승인인지 종료(COMPLETION) 승인인지 구분. */
    private BundleApprovalKind approvalKind;
    private UUID approverId;
    private GoalApprovalDecision decision;
    private LocalDateTime decidedAt;
    private String comment;
    private String completionSummary;
    private String completionEvidenceFiles;
    private List<GoalApprovalWatcherResDto> watchers;
}
