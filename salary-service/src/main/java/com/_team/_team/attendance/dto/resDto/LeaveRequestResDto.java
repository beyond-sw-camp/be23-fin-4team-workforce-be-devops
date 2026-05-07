package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import lombok.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveRequestResDto {

    private UUID leaveRequestId;
    private UUID memberId;
    private UUID companyId;
    private UUID companyLeaveTypeId;

    private LocalDate startDate;
    private LocalDate endDate;
    private Double usageDays;

    /** 비연속 사용 날짜 (예: ["2026-05-21","2026-05-23","2026-05-28"]). null/빈배열이면 startDate~endDate 연속 범위 */
    private List<String> plannedDates;

    private String reason;
    private String evidenceFileUrl;

    private UUID approvalRequestId;
    private LeaveApprovalStatus approvalStatus;
    private BalanceType deductedBalanceType;

    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;

    public static LeaveRequestResDto fromEntity(LeaveRequest leaveRequest) {
        return LeaveRequestResDto.builder()
                .leaveRequestId(leaveRequest.getLeaveRequestId())
                .memberId(leaveRequest.getMemberId())
                .companyId(leaveRequest.getCompanyId())
                .companyLeaveTypeId(leaveRequest.getCompanyLeaveTypeId())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .usageDays(leaveRequest.getUsageDays())
                .plannedDates(parsePlannedDates(leaveRequest.getPlannedDatesJson()))
                .reason(leaveRequest.getReason())
                .evidenceFileUrl(leaveRequest.getEvidenceFileUrl())
                .approvalRequestId(leaveRequest.getApprovalRequestId())
                .approvalStatus(leaveRequest.getApprovalStatus())
                .deductedBalanceType(leaveRequest.getDeductedBalanceType())
                .requestedAt(leaveRequest.getRequestedAt())
                .decidedAt(leaveRequest.getDecidedAt())
                .decidedBy(leaveRequest.getDecidedBy())
                .decisionNote(leaveRequest.getDecisionNote())
                .build();
    }

    private static final ObjectMapper PLANNED_DATES_MAPPER = new ObjectMapper();

    private static List<String> parsePlannedDates(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return PLANNED_DATES_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
