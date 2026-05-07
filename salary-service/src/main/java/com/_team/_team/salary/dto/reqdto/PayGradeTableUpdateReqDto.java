package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayGradeTableUpdateReqDto {

    @NotNull
    @PositiveOrZero
    private Long baseSalary;

    @Size(max = 200)
    private String description;
}