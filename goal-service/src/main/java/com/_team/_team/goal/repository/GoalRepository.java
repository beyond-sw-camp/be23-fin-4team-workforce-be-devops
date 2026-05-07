package com._team._team.goal.repository;

import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByCompanyId(UUID companyId);

    List<Goal> findByCompanyIdAndOwnerTypeOrderByCycleStartDateDesc(UUID companyId, GoalOwnerType ownerType);

    List<Goal> findByCompanyIdAndOwnerTypeAndOwnerIdInOrderByCycleStartDateDesc(
            UUID companyId, GoalOwnerType ownerType, Collection<UUID> ownerIds);

    List<Goal> findByCompanyIdAndOwnerTypeAndOwnerIdOrderByCycleStartDateDesc(
            UUID companyId, GoalOwnerType ownerType, UUID ownerId);

    List<Goal> findByCompanyIdAndOwnerIdOrderByCycleStartDateDesc(UUID companyId, UUID ownerId);

    List<Goal> findByCompanyIdAndOwnerIdAndStatus(UUID companyId, UUID ownerId, GoalStatus status);

    @Query("""
        SELECT g FROM Goal g
        WHERE g.companyId = :companyId
          AND g.ownerType = :ownerType
          AND g.ownerId IN :ownerIds
        ORDER BY g.cycleStartDate DESC
        """)
    List<Goal> findOrgScope(
            @Param("companyId") UUID companyId,
            @Param("ownerType") GoalOwnerType ownerType,
            @Param("ownerIds") Collection<UUID> ownerIds);

    @Query("""
        SELECT COALESCE(SUM(g.weightPct), 0)
        FROM Goal g
        WHERE g.ownerId = :ownerId
          AND g.cycle = :cycle
          AND g.cycleStartDate = :start
          AND g.status IN :statuses
        """)
    int sumWeight(
            @Param("ownerId") UUID ownerId,
            @Param("cycle") KpiCycle cycle,
            @Param("start") LocalDate start,
            @Param("statuses") Collection<GoalStatus> statuses);

    List<Goal> findByOwnerIdAndCycleAndCycleStartDate(
            UUID ownerId, KpiCycle cycle, LocalDate cycleStartDate);

    List<Goal> findByOwnerIdAndCycleAndCycleStartDateAndStatusIn(
            UUID ownerId, KpiCycle cycle, LocalDate cycleStartDate, Collection<GoalStatus> statuses);

    List<Goal> findByOwnerIdAndCycleAndStatusIn(
            UUID ownerId, KpiCycle cycle, Collection<GoalStatus> statuses);

    List<Goal> findByAlignedOrgGoalId(UUID alignedOrgGoalId);

    @Query("""
        SELECT DISTINCT g.ownerId FROM Goal g
        WHERE g.companyId = :companyId
          AND g.ownerType = 'MEMBER'
          AND g.status = 'ACTIVE'
          AND g.cycle = :cycle
          AND g.cycleStartDate = :cycleStartDate
        """)
    List<UUID> findMemberIdsWithActiveGoalByCycle(
            @Param("companyId") UUID companyId,
            @Param("cycle") KpiCycle cycle,
            @Param("cycleStartDate") LocalDate cycleStartDate);
}
