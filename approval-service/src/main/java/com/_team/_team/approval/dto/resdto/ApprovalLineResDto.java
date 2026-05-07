package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.Approval;
import com._team._team.approval.domain.enums.LineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalLineResDto {
    private UUID approvalId;
    private UUID requestId;
    private UUID approverMemberId;
    private UUID approverMemberPositionId;
    private Integer stepOrder;
    private LineStatus approvalStatus;
    private LocalDateTime actedAt;
    private String comment;
    private String signatureImageUrl;
    private String isSignedYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- 대결 관련 ---
    private UUID actualApproverMemberId;
    private UUID actualApproverMemberPositionId;
    private String isProxyYn;

    public static ApprovalLineResDto fromEntity(Approval entity) {
        return ApprovalLineResDto.builder()
                .approvalId(entity.getApprovalId())
                .requestId(entity.getRequest().getRequestId())
                .approverMemberId(entity.getApproverMemberId())
                .approverMemberPositionId(entity.getApproverMemberPositionId())
                .stepOrder(entity.getStepOrder())
                .approvalStatus(entity.getApprovalStatus())
                .actedAt(entity.getActedAt())
                .comment(entity.getComment())
                .signatureImageUrl(entity.getSignatureImageUrl())
                .isSignedYn(entity.getIsSignedYn())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .actualApproverMemberId(entity.getActualApproverMemberId())
                .actualApproverMemberPositionId(entity.getActualApproverMemberPositionId())
                .isProxyYn(entity.getIsProxyYn())
                .build();
    }
}
