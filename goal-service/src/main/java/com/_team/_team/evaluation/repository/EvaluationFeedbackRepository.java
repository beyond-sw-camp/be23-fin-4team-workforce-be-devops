package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.EvaluationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationFeedbackRepository extends JpaRepository<EvaluationFeedback, UUID> {

    List<EvaluationFeedback> findByResponseIdOrderByCreatedAtAsc(UUID responseId);

    List<EvaluationFeedback> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
}
