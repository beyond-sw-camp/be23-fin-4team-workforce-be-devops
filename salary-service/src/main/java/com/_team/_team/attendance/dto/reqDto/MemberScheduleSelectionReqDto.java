package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

/**
 *스케줄 선택 제출 요청 DTO
 * 1. 월 시작 전 첫 선택 (requestReason null 허용)
 * 2. 월 중 스케줄 변경 (requestReason 필수)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class MemberScheduleSelectionReqDto {

    /** "YYYY-MM" 형식, 예: "2026-05" */
    @NotBlank
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
            message = "형식은 YYYY-MM 이어야 합니다.")
    private String targetYearMonth;

    /** 선택한 스케줄 ID */
    @NotNull
    private UUID slotId;

    /**
     * 직원이 이번 달 사용할 점심, 휴게 시작 시각
     */
    private LocalTime breakStart;

    /** 직원이 이번 달 사용할 점심·휴게 종료 시각 (예: 13:00) */
    private LocalTime breakEnd;

    /**
     * 변경 사유
     */
    @Size(max = 500)
    private String requestReason;
}
