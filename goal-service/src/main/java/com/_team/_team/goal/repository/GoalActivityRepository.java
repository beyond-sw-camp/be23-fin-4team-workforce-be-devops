package com._team._team.goal.repository;

import com._team._team.goal.domain.GoalActivity;
import com._team._team.goal.domain.enums.GoalActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalActivityRepository extends JpaRepository<GoalActivity, UUID> {
    List<GoalActivity> findByGoal_GoalIdOrderByCreatedAtDesc(UUID goalId);
    List<GoalActivity> findByGoal_GoalIdAndTypeOrderByCreatedAtDesc(UUID goalId, GoalActivityType type);
    boolean existsByGoal_GoalIdAndType(UUID goalId, GoalActivityType type);

    Page<GoalActivity> findByGoal_GoalIdOrderByCreatedAtDesc(UUID goalId, Pageable pageable);
}
