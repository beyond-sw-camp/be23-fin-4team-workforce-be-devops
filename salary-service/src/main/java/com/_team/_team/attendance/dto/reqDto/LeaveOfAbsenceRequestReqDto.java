package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.LeaveOfAbsenceType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 직원 본인 휴직 신청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveOfAbsenceRequestReqDto {

    @NotNull(message = "휴직 종류는 필수입니다.")
    private LeaveOfAbsenceType type;

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다.")
    private LocalDate endDate;

    @NotBlank(message = "유급 여부는 필수입니다.")
    @Pattern(regexp = "^[YN]$", message = "Y 또는 N 만 허용됩니다.")
    private String isPaidYn;

    @Size(max = 500)
    private String reason;

    // 진단서, 출생증명서 등
    @Size(max = 500)
    private String evidenceFileUrl;
}