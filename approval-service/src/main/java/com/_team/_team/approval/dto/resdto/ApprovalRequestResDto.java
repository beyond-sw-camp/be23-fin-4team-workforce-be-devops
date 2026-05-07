package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.Approval;
import com._team._team.approval.domain.ApprovalRequest;
import com._team._team.approval.domain.ApprovalViewer;
import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.RequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalRequestResDto {
    private UUID requestId;
    private UUID documentId;
    private String documentName;
    private UUID memberId;
    private RequestType requestType;
    private String contentJson;
    private RequestStatus requestStatus;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String documentNumber;
    private List<OfficialRecipientResDto> recipients;
    private String isDeptVisibleYn;
    private List<ApprovalLineResDto> approvalLines;
    private List<ApprovalViewerResDto> viewers;
    private String sendYn;


    // === 요청자 스냅샷 ===
    private String requesterName;
    private UUID requesterOrganizationId;
    private String requesterOrganizationName;

    private String formSchemaSnapshot;

    // 기존 메서드 그대로
    public static ApprovalRequestResDto fromEntity(ApprovalRequest entity,
                                                                                        List<Approval> approvals,
                                                                                        List<ApprovalViewer> viewers,
                                                                                        List<OfficialRecipientResDto> recipients) {

        return ApprovalRequestResDto.builder()
                .requestId(entity.getRequestId())
                .documentId(entity.getApprovalDocument().getDocumentId())
                .documentName(entity.getApprovalDocument().getDocumentName())
                .memberId(entity.getMemberId())
                .requestType(entity.getRequestType())
                .contentJson(entity.getContentJson())
                .requestStatus(entity.getRequestStatus())
                .cancelReason(entity.getCancelReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .requesterName(entity.getRequesterName())
                .requesterOrganizationId(entity.getRequesterOrganizationId())
                .requesterOrganizationName(entity.getRequesterOrganizationName())
                .approvalLines(approvals.stream()
                        .map(ApprovalLineResDto::fromEntity)
                        .collect(Collectors.toList()))
                .viewers(viewers.stream()
                        .map(ApprovalViewerResDto::fromEntity)
                        .collect(Collectors.toList()))
                .documentNumber(entity.getDocumentNumber())
                .recipients(recipients)
                .isDeptVisibleYn(entity.getIsDeptVisibleYn())
                .formSchemaSnapshot(entity.getFormSchemaSnapshot())
                .sendYn(entity.getSendYn())
                .build();
    }

    // === 비공개 마스킹 메서드 추가 ===
    public static ApprovalRequestResDto maskedFromEntity(ApprovalRequest entity) {
        return ApprovalRequestResDto.builder()
                .requestId(entity.getRequestId())
                .memberId(entity.getMemberId())
                .requestStatus(entity.getRequestStatus())
                .createdAt(entity.getCreatedAt())
                .requesterName(entity.getRequesterName())
                .isDeptVisibleYn("N")
                // 나머지는 전부 마스킹
                .documentId(null)
                .documentName("비공개 문서입니다")
                .requestType(null)
                .contentJson(null)
                .cancelReason(null)
                .updatedAt(null)
                .documentNumber(null)
                .requesterOrganizationId(null)
                .requesterOrganizationName(null)
                .approvalLines(List.of())
                .viewers(List.of())
                .recipients(null)
                .build();
    }
}