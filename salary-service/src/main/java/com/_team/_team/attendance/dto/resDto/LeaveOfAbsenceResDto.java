package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveOfAbsenceResDto {

    private UUID leaveOfAbsenceId;
    private UUID memberId;
    private UUID companyId;
    private LeaveOfAbsenceType type;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualEndDate;

    private String isPaidYn;
    private String reason;
    private String evidenceFileUrl;

    private UUID approvalRequestId;
    private LeaveOfAbsenceApprovalStatus status;

    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;

    public static LeaveOfAbsenceResDto fromEntity(MemberLeaveOfAbsence loa) {
        return LeaveOfAbsenceResDto.builder()
                .leaveOfAbsenceId(loa.getLeaveOfAbsenceId())
                .memberId(loa.getMemberId())
                .companyId(loa.getCompanyId())
                .type(loa.getType())
                .startDate(loa.getStartDate())
                .endDate(loa.getEndDate())
                .actualEndDate(loa.getActualEndDate())
                .isPaidYn(loa.getIsPaidYn())
                .reason(loa.getReason())
                .evidenceFileUrl(loa.getEvidenceFileUrl())
                .approvalRequestId(loa.getApprovalRequestId())
                .status(loa.getStatus())
                .requestedAt(loa.getRequestedAt())
                .decidedAt(loa.getDecidedAt())
                .decidedBy(loa.getDecidedBy())
                .decisionNote(loa.getDecisionNote())
                .build();
    }
}