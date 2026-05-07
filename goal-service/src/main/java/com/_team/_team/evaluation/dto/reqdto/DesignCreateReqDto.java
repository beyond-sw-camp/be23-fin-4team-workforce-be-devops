package com._team._team.evaluation.dto.reqdto;

import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.converter.GradeConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * [D-4] 평가 설계 생성 요청 DTO.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DesignCreateReqDto {
    @NotBlank private String name;

    private List<EvaluationSection> sections;
    private GradeConfig gradeConfig;

}
