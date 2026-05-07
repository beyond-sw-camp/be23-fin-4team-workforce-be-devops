package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.GoalComment;
import com._team._team.goal.domain.converter.Reaction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GoalCommentResDto {
    private UUID commentId;
    private UUID goalId;
    private UUID authorId;
    private String body;
    private List<Reaction> reactions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoalCommentResDto from(GoalComment c) {
        return GoalCommentResDto.builder()
                .commentId(c.getCommentId())
                .goalId(c.getGoal().getGoalId())
                .authorId(c.getAuthorId())
                .body(c.getBody())
                .reactions(c.getReactions() != null ? c.getReactions() : Collections.emptyList())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
