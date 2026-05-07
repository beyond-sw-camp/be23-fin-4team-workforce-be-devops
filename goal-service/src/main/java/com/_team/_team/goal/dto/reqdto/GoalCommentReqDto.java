package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GoalCommentReqDto {
    @NotBlank
    @Size(max = 8000)
    private String body;
}
