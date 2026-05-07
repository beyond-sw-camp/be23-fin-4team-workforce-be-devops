package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정정 신청 반려 사유 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceCorrectionRejectReqDto {

    @NotBlank(message = "반려 사유는 필수입니다.")
    private String rejectReason;
}
