package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EvaluationGroupRepository extends JpaRepository<EvaluationGroup, UUID> {
    List<EvaluationGroup> findBySeason_SeasonId(UUID seasonId);
    List<EvaluationGroup> findByCompanyId(UUID companyId);
    long countByDesign_DesignId(UUID designId);
}
