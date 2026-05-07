package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// 관리자가 입사자에게 기본 수당 등록
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberAllowanceAutoGrantReqDto {

    @NotNull
    private UUID memberId;

    @NotNull
    private UUID salaryItemTemplateId;

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    private LocalDate effectiveFrom;
}