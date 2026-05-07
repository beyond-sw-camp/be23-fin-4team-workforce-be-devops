package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class GoalCommentReactionReqDto {
    @NotBlank
    private String emoji;
    @NotNull
    private UUID memberId;
}
