package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// 직원 수당 변경 신청, 결재 요청하는 PENDING 생성용
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberAllowanceRequestReqDto {

    @NotNull(message = "수당 항목 ID 는 필수입니다.")
    private UUID salaryItemTemplateId;

    @NotNull(message = "금액은 필수입니다.")
    @Positive(message = "금액은 0 보다 커야합니다.")
    private Long amount;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    @NotNull(message = "변경 사유는 필수입니다.")
    @Size(min = 1, max = 500)
    private String reason;
}