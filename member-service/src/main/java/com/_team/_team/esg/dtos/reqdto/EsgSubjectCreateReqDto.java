package com._team._team.esg.dtos.reqdto;

import com._team._team.esg.domain.enums.EsgCategory;
import com._team._team.esg.domain.enums.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgSubjectCreateReqDto {

    @NotBlank(message = "활동 제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "카테고리는 필수입니다.")
    private EsgCategory category;


    @Positive(message = "포인트는 0보다 커야 합니다.")
    private int defaultPoints;
}