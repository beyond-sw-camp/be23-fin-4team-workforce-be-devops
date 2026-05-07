package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class LeavePromotionDesignateReqDto {

    @NotEmpty(message = "지정할 날짜를 한 개 이상 입력해주세요")
    private List<LocalDate> dates;

    @NotBlank(message = "사유는 필수입니다")
    private String reason;
}