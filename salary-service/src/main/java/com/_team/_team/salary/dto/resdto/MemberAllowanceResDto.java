package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberAllowanceResDto {

    private UUID memberAllowanceId;
    private UUID memberId;
    private UUID salaryItemTemplateId;
    private Long amount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private AllowanceApprovalStatus approvalStatus;
    private String reason;
    private UUID approvalRequestId;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;

    public static MemberAllowanceResDto fromEntity(MemberAllowance entity) {
        return MemberAllowanceResDto.builder()
                .memberAllowanceId(entity.getMemberAllowanceId())
                .memberId(entity.getMemberId())
                .salaryItemTemplateId(entity.getSalaryItemTemplateId())
                .amount(entity.getAmount())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .approvalStatus(entity.getApprovalStatus())
                .reason(entity.getReason())
                .approvalRequestId(entity.getApprovalRequestId())
                .requestedAt(entity.getRequestedAt())
                .decidedAt(entity.getDecidedAt())
                .decidedBy(entity.getDecidedBy())
                .decisionNote(entity.getDecisionNote())
                .build();
    }
}