package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.enums.ScheduleApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 슬롯 선택 응답 DTO
 * 개인 조회, 관리자 결재 화면에서 사용
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class MemberScheduleSelectionResDto {

    private UUID selectionId;
    private UUID memberId;
    private String targetYearMonth;
    private UUID slotId;
    /** FLEXIBLE 직원이 매월 선택한 점심·휴게 시작 시각 */
    private LocalTime breakStart;
    /** FLEXIBLE 직원이 매월 선택한 점심·휴게 종료 시각 */
    private LocalTime breakEnd;
    /** breakStart/breakEnd 로부터 계산된 점심 분(편의용) */
    private Integer breakMinutes;
    private ScheduleApprovalStatus approvalStatus;
    private String requestReason;
    private UUID requestedBy;
    private LocalDateTime requestedAt;
    private UUID approvalRequestId;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;

    public static MemberScheduleSelectionResDto fromEntity(MemberScheduleSelection s) {
        return MemberScheduleSelectionResDto.builder()
                .selectionId(s.getSelectionId())
                .memberId(s.getMemberId())
                .targetYearMonth(s.getTargetYearMonth())
                .slotId(s.getSlotId())
                .breakStart(s.getBreakStart())
                .breakEnd(s.getBreakEnd())
                .breakMinutes(s.computeBreakMinutes())
                .approvalStatus(s.getApprovalStatus())
                .requestReason(s.getRequestReason())
                .requestedBy(s.getRequestedBy())
                .requestedAt(s.getRequestedAt())
                .approvalRequestId(s.getApprovalRequestId())
                .decidedAt(s.getDecidedAt())
                .decidedBy(s.getDecidedBy())
                .decisionNote(s.getDecisionNote())
                .build();
    }
}
