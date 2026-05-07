package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

/**
 * 시차출퇴근 슬롯 생성 요청 DTO
 * 관리자가 FLEXIBLE 스케줄에 속한 슬롯을 등록
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class FlexibleTimeSlotCreateReqDto {

    /** 소속 WorkSchedule (workType=FLEXIBLE 이어야 함) */
    @NotNull
    private UUID workScheduleId;

    /** 시스템 식별 코드 (미입력 시 서버가 자동 생성) */
    @Size(max = 30)
    private String slotCode;

    /** UI 노출용 이름 */
    @NotBlank
    @Size(max = 50)
    private String slotLabel;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @Positive
    private Integer workMinutes;

    /** 슬롯 점심 시작 시각 */
    private LocalTime breakStart;

    /** 슬롯 점심 종료 시각 */
    private LocalTime breakEnd;

    /**
     * 기본 슬롯 여부
     */
    private Boolean isDefault;

    public FlexibleTimeSlot toEntity(UUID companyId) {
        return FlexibleTimeSlot.builder()
                .workScheduleId(workScheduleId)
                .companyId(companyId)
                .slotCode(slotCode)
                .slotLabel(slotLabel)
                .startTime(startTime)
                .endTime(endTime)
                .workMinutes(workMinutes)
                .breakStart(breakStart)
                .breakEnd(breakEnd)
                .isDefault(isDefault != null && isDefault)
                .activeYn("Y")
                .build();
    }
}