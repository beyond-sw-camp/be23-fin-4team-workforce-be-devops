package com._team._team.goal.repository;

import com._team._team.goal.domain.GoalComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalCommentRepository extends JpaRepository<GoalComment, UUID> {
    List<GoalComment> findByGoal_GoalIdOrderByCreatedAtAsc(UUID goalId);
}
