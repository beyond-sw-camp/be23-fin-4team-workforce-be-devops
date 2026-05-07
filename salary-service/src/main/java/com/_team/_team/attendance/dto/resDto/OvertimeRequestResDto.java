package com._team._team.attendance.dto.resDto;


import com._team._team.attendance.domain.OvertimeRequest;
import com._team._team.attendance.domain.enums.OvertimeApprovalStatus;
import com._team._team.attendance.domain.enums.OvertimeRequestType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class OvertimeRequestResDto {

    private UUID overtimeRequestId;
    private UUID memberId;
    private LocalDate targetDate;
    private OvertimeRequestType requestType;

    // PRE
    private LocalTime plannedStartTime;
    private LocalTime plannedEndTime;
    private Integer requestedMinutes;

    // POST
    private LocalTime actualStartTime;
    private LocalTime actualEndTime;
    private Integer actualMinutes;

    private String reason;
    private OvertimeApprovalStatus approvalStatus;
    private UUID approvalRequestId;
    private Integer approvedMinutes;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;

    public static OvertimeRequestResDto fromEntity(OvertimeRequest r) {
        return OvertimeRequestResDto.builder()
                .overtimeRequestId(r.getOvertimeRequestId())
                .memberId(r.getMemberId())
                .targetDate(r.getTargetDate())
                .requestType(r.getRequestType())
                .plannedStartTime(r.getPlannedStartTime())
                .plannedEndTime(r.getPlannedEndTime())
                .requestedMinutes(r.getRequestedMinutes())
                .actualStartTime(r.getActualStartTime())
                .actualEndTime(r.getActualEndTime())
                .actualMinutes(r.getActualMinutes())
                .reason(r.getReason())
                .approvalStatus(r.getApprovalStatus())
                .approvalRequestId(r.getApprovalRequestId())
                .approvedMinutes(r.getApprovedMinutes())
                .submittedAt(r.getSubmittedAt())
                .decidedAt(r.getDecidedAt())
                .decidedBy(r.getDecidedBy())
                .decisionNote(r.getDecisionNote())
                .build();
    }
}