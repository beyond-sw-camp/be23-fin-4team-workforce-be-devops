package com._team._team.salary.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// DRAFT 상태 협상안 수정 모든 필드 옵션 변경값만 보냄
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryNegotiationUpdateReqDto {
    private Long proposedBaseSalary;
    private String proposedJobGradeName;
    private String proposedJobTitleName;
    private LocalDate proposedEffectiveFrom;
    private String reason;
}
