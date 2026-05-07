package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationResponseRepository extends JpaRepository<EvaluationResponse, UUID> {
    @Query("""
            select case when count(r) > 0 then true else false end
            from EvaluationResponse r
            where r.evaluatorId = :evaluatorId
              and r.companyId = :companyId
              and r.group.design.designId = :designId
            """)
    boolean existsEvaluatorAssignmentForDesign(
            @Param("evaluatorId") UUID evaluatorId,
            @Param("companyId") UUID companyId,
            @Param("designId") UUID designId);

    Optional<EvaluationResponse> findByGroup_GroupIdAndTargetMemberId(UUID groupId, UUID targetMemberId);

    List<EvaluationResponse> findByCompanyIdAndTargetMemberIdOrderByCreatedAtDesc(
            UUID companyId, UUID targetMemberId);

    List<EvaluationResponse> findByCompanyIdAndStageOrderByCreatedAtDesc(
            UUID companyId, EvaluationStage stage);

    List<EvaluationResponse> findByCompanyIdAndEvaluatorIdAndStageOrderByCreatedAtDesc(
            UUID companyId, UUID evaluatorId, EvaluationStage stage);

    List<EvaluationResponse> findByCompanyIdAndEvaluatorIdOrderByCreatedAtDesc(
            UUID companyId, UUID evaluatorId);

    List<EvaluationResponse> findByGroup_GroupIdOrderByCreatedAtAsc(UUID groupId);

    /** 시즌 단위 일괄 조회 */
    List<EvaluationResponse> findByGroup_GroupIdIn(List<UUID> groupIds);

    List<EvaluationResponse> findByGroup_Season_SeasonId(UUID seasonId);
}
