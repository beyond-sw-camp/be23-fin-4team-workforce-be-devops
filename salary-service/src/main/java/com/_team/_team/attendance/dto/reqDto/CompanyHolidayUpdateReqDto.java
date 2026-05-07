package com._team._team.attendance.dto.reqDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 공휴일 수정 요청 DTO
 * - 공휴일명, 날짜, 유급여부 수정 가능
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CompanyHolidayUpdateReqDto {

    /** 공휴일명 (예: "사내 공휴일", "임시 공휴일") */
    private String holidayName;

    /** 공휴일 날짜 */
    private LocalDate holidayDate;

    /** 유급 여부 ('Y' = 유급, 'N' = 무급) */
    private String isPaidYn;
}