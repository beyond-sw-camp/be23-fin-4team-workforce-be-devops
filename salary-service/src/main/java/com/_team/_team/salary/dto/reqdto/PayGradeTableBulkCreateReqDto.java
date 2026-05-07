package com._team._team.salary.dto.reqdto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayGradeTableBulkCreateReqDto {

    @NotNull
    private LocalDate effectiveFrom;

    @NotNull
    @Size(min = 1, message = "최소 1건 이상 입력하세요.")
    @Valid
    private List<Entry> entries;

    @Getter
    @NoArgsConstructor
    public static class Entry {
        @NotNull
        @Positive
        private Integer step;

        @NotNull
        @PositiveOrZero
        private Long baseSalary;

        @Size(max = 200)
        private String description;
    }
}