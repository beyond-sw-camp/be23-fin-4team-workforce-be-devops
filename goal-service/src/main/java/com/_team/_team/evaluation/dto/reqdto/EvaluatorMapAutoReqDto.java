package com._team._team.evaluation.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EvaluatorMapAutoReqDto {
    @NotBlank private String basis; // direct_leader, team_leader, job_grade
}
