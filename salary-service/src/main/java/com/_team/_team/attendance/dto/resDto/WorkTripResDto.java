package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.domain.enums.ExpenseStatus;
import com._team._team.attendance.domain.enums.ExpenseType;
import com._team._team.attendance.domain.enums.WorkTripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 출장/외근 응답 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkTripResDto {
    private UUID workTripDetailId;
    private UUID memberId;
    private WorkTripType workTripType;
    private String destination;
    private String purpose;
    private Long expenseAmount;
    private ExpenseType expenseType;
    private ExpenseStatus expenseStatus;
    /** DailyAttendance의 날짜 — 프론트에서 날짜별 표시용 */
    private LocalDate attendanceDate;

    public static WorkTripResDto fromEntity(WorkTripDetail detail) {
        return WorkTripResDto.builder()
                .workTripDetailId(detail.getWorkTripDetailId())
                .memberId(detail.getMemberId())
                .workTripType(detail.getWorkTripType())
                .destination(detail.getDestination())
                .purpose(detail.getPurpose())
                .expenseAmount(detail.getExpenseAmount())
                .expenseType(detail.getExpenseType())
                .expenseStatus(detail.getExpenseStatus())
                .attendanceDate(detail.getDailyAttendance() != null
                        ? detail.getDailyAttendance().getAttendanceDate()
                        : null)
                .build();
    }
}

