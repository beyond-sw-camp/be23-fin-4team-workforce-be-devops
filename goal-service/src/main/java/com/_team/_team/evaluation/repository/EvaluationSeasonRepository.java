package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationSeasonRepository extends JpaRepository<EvaluationSeason, UUID> {

    List<EvaluationSeason> findByCompanyIdOrderByStartDateDesc(UUID companyId);

    List<EvaluationSeason> findByCompanyIdAndStatus(UUID companyId, SeasonStatus status);
}
