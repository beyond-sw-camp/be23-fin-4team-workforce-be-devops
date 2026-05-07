package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.GoalApprovalBundle;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import com._team._team.goal.domain.enums.GoalApprovalDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BundleResDto {

    private UUID bundleId;
    private UUID companyId;
    private UUID requestedBy;
    private LocalDateTime requestedAt;

    private String cycleKey;
    private int revision;
    private UUID originalBundleId;

    private int weightSumSnapshot;

    private BundleApprovalStatus status;
    private GoalApprovalDecision decision;

    private UUID approverId;
    private LocalDateTime decidedAt;

    private String commentText;
    private String rejectionReason;
    private String lastRejectedReason;

    private List<UUID> goalIds;
    private List<UUID> affectedGoalIds;
    private List<UUID> watcherIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BundleResDto from(GoalApprovalBundle b) {
        return BundleResDto.builder()
                .bundleId(b.getBundleId())
                .companyId(b.getCompanyId())
                .requestedBy(b.getRequestedBy())
                .requestedAt(b.getRequestedAt())
                .cycleKey(b.getCycleKey())
                .revision(b.getRevision())
                .originalBundleId(b.getOriginalBundleId())
                .weightSumSnapshot(b.getWeightSumSnapshot())
                .status(b.getStatus())
                .decision(b.getDecision())
                .approverId(b.getApproverId())
                .decidedAt(b.getDecidedAt())
                .commentText(b.getCommentText())
                .rejectionReason(b.getRejectionReason())
                .lastRejectedReason(b.getLastRejectedReason())
                .goalIds(b.getGoalIds() != null ? b.getGoalIds() : Collections.emptyList())
                .affectedGoalIds(b.getAffectedGoalIds() != null ? b.getAffectedGoalIds() : Collections.emptyList())
                .watcherIds(b.getWatcherIds() != null ? b.getWatcherIds() : Collections.emptyList())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
