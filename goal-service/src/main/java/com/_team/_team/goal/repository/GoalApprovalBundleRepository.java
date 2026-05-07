package com._team._team.goal.repository;

import com._team._team.goal.domain.GoalApprovalBundle;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalApprovalBundleRepository extends JpaRepository<GoalApprovalBundle, UUID> {

    /** 같은 cycleKey 에 PENDING bundle 이 이미 있는지 — 중복 제출 방지 */
    Optional<GoalApprovalBundle> findFirstByRequestedByAndCycleKeyAndStatus(
            UUID requestedBy, String cycleKey, BundleApprovalStatus status);

    boolean existsByRequestedByAndCycleKeyAndStatus(
            UUID requestedBy, String cycleKey, BundleApprovalStatus status);

    /** 내가 요청한 bundle 들 (히스토리 포함) */
    List<GoalApprovalBundle> findByRequestedByAndCompanyIdOrderByRequestedAtDesc(
            UUID requestedBy, UUID companyId);

    /** 본인이 요청한 특정 status bundle — SeasonActivation 의 PENDING 잔존 검증 */
    List<GoalApprovalBundle> findByRequestedByAndCompanyIdAndStatus(
            UUID requestedBy, UUID companyId, BundleApprovalStatus status);

    /** 내가 처리해야 할 bundle */
    List<GoalApprovalBundle> findByApproverIdAndCompanyIdAndStatusOrderByRequestedAtDesc(
            UUID approverId, UUID companyId, BundleApprovalStatus status);

    Optional<GoalApprovalBundle> findFirstByRequestedByAndCycleKeyOrderByRevisionDesc(
            UUID requestedBy, String cycleKey);
}
