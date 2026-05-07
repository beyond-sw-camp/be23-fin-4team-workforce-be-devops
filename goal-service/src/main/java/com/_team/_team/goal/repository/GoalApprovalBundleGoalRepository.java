package com._team._team.goal.repository;

import com._team._team.goal.domain.GoalApprovalBundleGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalApprovalBundleGoalRepository extends JpaRepository<GoalApprovalBundleGoal, UUID> {

    List<GoalApprovalBundleGoal> findByBundle_BundleId(UUID bundleId);

    Optional<GoalApprovalBundleGoal> findFirstByGoal_GoalIdOrderByBundle_RequestedAtDesc(UUID goalId);
}
