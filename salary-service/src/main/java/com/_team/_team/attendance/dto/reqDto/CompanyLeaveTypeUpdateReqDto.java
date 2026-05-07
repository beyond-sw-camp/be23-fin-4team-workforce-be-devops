package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.BalanceType;
import jakarta.validation.constraints.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompanyLeaveTypeUpdateReqDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    // 시스템 기본이면 이하 필드는 서비스에서 무시됨
    private BalanceType balanceType;
    private Double daysPerUse;
    private String isPaidYn;
    private Double maxDaysPerYear;
    private String requireEvidenceYn;

    @NotNull
    @PositiveOrZero
    private Integer displayOrder;
}
