package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.domain.enums.ExpenseType;
import com._team._team.attendance.domain.enums.WorkTripType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;


/**
 * 출장/외근 등록 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkTripCreateReqDto {

    @NotNull(message = "날짜는 필수 입니다.")
    private LocalDate date; // 출장/외근 날짜

    @NotNull(message = "출장/외근 유형은 필수입니다.")
    private WorkTripType workTripType; // BUSINESS_TRIP(출장) / OUTSIDE_WORK(외근)

    private String destination; // 목적지 주소
    private String purpose; // 목적

    /** 경비 관련 (nullable — 경비 없는 외근도 있음) */
    private Long expenseAmount; // 경비 금액(원)
    private ExpenseType expenseType; // 경비 유형

    public WorkTripDetail toEntity(DailyAttendance dailyAttendance, UUID companyId, UUID memberId) {
        return WorkTripDetail.builder()
                .memberId(memberId)
                .companyId(companyId)
                .workTripType(this.workTripType)
                .destination(this.destination)
                .purpose(this.purpose)
                .expenseAmount(this.expenseAmount)
                .expenseType(this.expenseType)
                .dailyAttendance(dailyAttendance)
                .build();
    }
}

