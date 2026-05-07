package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.BalanceType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CompanyLeaveTypeCreateReqDto {

    @Size(max = 50)
    @Pattern(regexp = "^[A-Z_]+$", message = "코드는 대문자와 언더스코어만 허용")
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    // null 이면 차감 없음 (경조, 예비군 등)
    private BalanceType balanceType;

    @NotNull
    @Positive
    private Double daysPerUse;

    @NotBlank
    @Pattern(regexp = "^[YN]$")
    private String isPaidYn;

    @Positive
    private Double maxDaysPerYear;

    @NotBlank
    @Pattern(regexp = "^[YN]$")
    private String requireEvidenceYn;

    @NotNull
    @PositiveOrZero
    private Integer displayOrder;
}