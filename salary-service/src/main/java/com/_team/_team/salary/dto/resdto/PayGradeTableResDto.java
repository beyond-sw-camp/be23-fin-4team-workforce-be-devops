package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.PayGradeTable;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayGradeTableResDto {

    private UUID payGradeTableId;
    private Integer step;
    private Long baseSalary;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String description;

    public static PayGradeTableResDto fromEntity(PayGradeTable e) {
        return PayGradeTableResDto.builder()
                .payGradeTableId(e.getPayGradeTableId())
                .step(e.getStep())
                .baseSalary(e.getBaseSalary())
                .effectiveFrom(e.getEffectiveFrom())
                .effectiveTo(e.getEffectiveTo())
                .description(e.getDescription())
                .build();
    }
}