package com._team._team.evaluation.repository;

import com._team._team.evaluation.domain.CalibrationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CalibrationHistoryRepository extends JpaRepository<CalibrationHistory, UUID> {
    List<CalibrationHistory> findBySeasonIdOrderByCreatedAtDesc(UUID seasonId);
    List<CalibrationHistory> findByResponseIdOrderByCreatedAtDesc(UUID responseId);
}
