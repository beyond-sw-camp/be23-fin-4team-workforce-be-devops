package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.SalaryNegotiation;
import com._team._team.salary.domain.enums.NegotiationStatus;
import com._team._team.salary.domain.enums.NegotiationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 연봉 협상 응답 화면용 직원 정보 (sabun/name) 결합 옵션
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryNegotiationResDto {

    private UUID negotiationId;
    private UUID companyId;
    private UUID memberId;

    private NegotiationType negotiationType;
    private UUID groupId;
    private String groupName;

    // member-service 결합
    private String sabun;
    private String memberName;
    private String organizationName;

    private Long currentBaseSalary;
    private String currentJobGradeName;
    private String currentJobTitleName;

    private Long proposedBaseSalary;
    private String proposedJobGradeName;
    private String proposedJobTitleName;
    private LocalDate proposedEffectiveFrom;
    private Double changeRate;
    private String reason;

    private NegotiationStatus status;
    private UUID approvalRequestId;

    private LocalDateTime proposedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime appliedAt;
    private UUID decidedBy;
    private String decisionNote;

    private UUID appliedSalaryId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryNegotiationResDto fromEntity(SalaryNegotiation n) {
        return fromEntity(n, null, null, null);
    }

    public static SalaryNegotiationResDto fromEntity(SalaryNegotiation n,
                                                     String sabun,
                                                     String memberName,
                                                     String organizationName) {
        return SalaryNegotiationResDto.builder()
                .negotiationId(n.getNegotiationId())
                .companyId(n.getCompanyId())
                .memberId(n.getMemberId())
                .negotiationType(n.getNegotiationType())
                .groupId(n.getGroupId())
                .groupName(n.getGroupName())
                .sabun(sabun)
                .memberName(memberName)
                .organizationName(organizationName)
                .currentBaseSalary(n.getCurrentBaseSalary())
                .currentJobGradeName(n.getCurrentJobGradeName())
                .currentJobTitleName(n.getCurrentJobTitleName())
                .proposedBaseSalary(n.getProposedBaseSalary())
                .proposedJobGradeName(n.getProposedJobGradeName())
                .proposedJobTitleName(n.getProposedJobTitleName())
                .proposedEffectiveFrom(n.getProposedEffectiveFrom())
                .changeRate(n.getChangeRate())
                .reason(n.getReason())
                .status(n.getStatus())
                .approvalRequestId(n.getApprovalRequestId())
                .proposedAt(n.getProposedAt())
                .decidedAt(n.getDecidedAt())
                .appliedAt(n.getAppliedAt())
                .decidedBy(n.getDecidedBy())
                .decisionNote(n.getDecisionNote())
                .appliedSalaryId(n.getAppliedSalaryId())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
