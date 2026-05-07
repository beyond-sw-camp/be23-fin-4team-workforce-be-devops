package com._team._team.attendance.dto.resDto;
import com._team._team.attendance.domain.FlexibleTimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;


/**
 * 시차출퇴근 슬롯 응답 DTO
 * 슬롯 목록 조회, 선택 UI 노출 시 사용
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class FlexibleTimeSlotResDto {
    private UUID slotId;
    private UUID workScheduleId;
    private String slotCode;
    private String slotLabel;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer workMinutes;
    /** 슬롯에 박힌 점심·휴게 시작 시각 - 직원이 슬롯을 고르면 자동 적용 */
    private LocalTime breakStart;
    /** 슬롯에 박힌 점심·휴게 종료 시각 */
    private LocalTime breakEnd;
    private Boolean isDefault;
    private String activeYn;

    public static FlexibleTimeSlotResDto fromEntity(FlexibleTimeSlot slot) {
        return FlexibleTimeSlotResDto.builder()
                .slotId(slot.getSlotId())
                .workScheduleId(slot.getWorkScheduleId())
                .slotCode(slot.getSlotCode())
                .slotLabel(slot.getSlotLabel())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .workMinutes(slot.getWorkMinutes())
                .breakStart(slot.getBreakStart())
                .breakEnd(slot.getBreakEnd())
                .isDefault(slot.getIsDefault())
                .activeYn(slot.getActiveYn())
                .build();
    }
}
