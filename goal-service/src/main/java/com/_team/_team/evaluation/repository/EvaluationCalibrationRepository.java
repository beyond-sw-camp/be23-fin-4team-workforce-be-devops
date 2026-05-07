package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationCalibrationRepository extends JpaRepository<EvaluationCalibration, UUID> {

    List<EvaluationCalibration> findByResponseId(UUID responseId);

    Optional<EvaluationCalibration> findByResponseIdAndEvaluatorId(UUID responseId, UUID evaluatorId);

    Optional<EvaluationCalibration> findByResponseIdAndRole(UUID responseId, CalibrationRole role);

    List<EvaluationCalibration> findByEvaluatorId(UUID evaluatorId);
}
