package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.NegotiationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

// 단건 협상 등록 요청
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryNegotiationCreateReqDto {

    @NotNull(message = "직원 ID 필수")
    private UUID memberId;

    @NotNull(message = "협상 종류 필수")
    private NegotiationType negotiationType;

    @NotNull(message = "제안 기본급 필수")
    private Long proposedBaseSalary;

    private String proposedJobGradeName;
    private String proposedJobTitleName;

    @NotNull(message = "적용 시작일 필수")
    private LocalDate proposedEffectiveFrom;

    private String reason;
}
