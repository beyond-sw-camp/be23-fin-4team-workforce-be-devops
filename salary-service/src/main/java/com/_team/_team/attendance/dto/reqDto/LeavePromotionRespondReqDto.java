package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// 직원 사용 계획 회신 요청, 입력 받는 날짜는 참고용 계획이며 실제 잔여 차감과 무관
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class LeavePromotionRespondReqDto {

    @NotEmpty(message = "사용 계획 날짜를 한 개 이상 입력해주세요")
    private List<LocalDate> plannedDates;
}
