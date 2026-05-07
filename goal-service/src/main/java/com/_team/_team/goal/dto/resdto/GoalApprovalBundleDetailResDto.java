package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.enums.BundleApprovalKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GoalApprovalBundleDetailResDto {
    private UUID requestId;
    /** pending | approved | rejected */
    private String status;
    /** 번들이 활성화(ACTIVATION) 승인인지 종료(COMPLETION) 승인인지 구분. */
    private BundleApprovalKind approvalKind;
    private String rejectionReason;
    private List<GoalKrResDto> goals;
    private UUID approverId;
    private String decision;
    private LocalDateTime decidedAt;
    private String comment;
    private String completionSummary;
    private String completionEvidenceFiles;
    private List<GoalApprovalWatcherResDto> watchers;
}
