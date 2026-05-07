package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalTime;


/**
 * 시차출퇴근 스케줄 수정 요청 DTO
 * 관리자가 기존 스케줄의 시간 구성이나 라벨을 바꿀 때 사용
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class FlexibleTimeSlotUpdateReqDto {
    @Size(max = 50)
    private String slotLabel;

    private LocalTime startTime;
    private LocalTime endTime;

    @Positive
    private Integer workMinutes;

    /** 슬롯 점심 시작/종료 시각 - null 이면 미변경  */
    private LocalTime breakStart;
    private LocalTime breakEnd;
}
