package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationDesign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationDesignRepository extends JpaRepository<EvaluationDesign, UUID> {
    List<EvaluationDesign> findByCompanyId(UUID companyId);
    Optional<EvaluationDesign> findFirstByCompanyIdAndDefaultTemplateTrue(UUID companyId);
    Optional<EvaluationDesign> findFirstByCompanyIdOrderByCreatedAtAsc(UUID companyId);
    boolean existsByCompanyIdAndDefaultTemplateTrue(UUID companyId);
}
