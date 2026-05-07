package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.ApprovalViewer;
import com._team._team.approval.domain.enums.ViewerReadStatus;
import com._team._team.approval.domain.enums.ViewerType;
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
public class ApprovalViewerResDto {

    private UUID viewerId;
    private UUID requestId;
    private UUID viewerMemberId;
    private UUID viewerMemberPositionId;
    private ViewerType viewerType;
    private ViewerReadStatus viewerReadStatus;
    private LocalDateTime viewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApprovalViewerResDto fromEntity(ApprovalViewer entity) {
        return ApprovalViewerResDto.builder()
                .viewerId(entity.getViewerId())
                .requestId(entity.getApprovalRequest().getRequestId())
                .viewerMemberId(entity.getViewerMemberId())
                .viewerMemberPositionId(entity.getViewerMemberPositionId())
                .viewerType(entity.getViewerType())
                .viewerReadStatus(entity.getViewerReadStatus())
                .viewedAt(entity.getViewedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
