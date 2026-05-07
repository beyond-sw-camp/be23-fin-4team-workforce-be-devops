package com._team._team.goal.service;

import com._team._team.dto.BusinessException;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.enums.GoalApprovalStatus;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import com._team._team.goal.dto.reqdto.GoalCreateReqDto;
import com._team._team.goal.dto.reqdto.GoalUpdateReqDto;
import com._team._team.goal.dto.resdto.GoalCycleResDto;
import com._team._team.goal.dto.resdto.GoalKrResDto;
import com._team._team.goal.dto.resdto.GoalObjectiveResDto;
import com._team._team.goal.dto.resdto.GoalViewMapper;
import com._team._team.goal.dto.resdto.GoalViewResDto;
import com._team._team.goal.feignclients.MemberServiceClient;
import com._team._team.goal.repository.GoalRepository;
import com._team._team.goal.util.CycleKeyResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final MemberServiceClient memberServiceClient;

    public GoalService(GoalRepository goalRepository,
                       MemberServiceClient memberServiceClient) {
        this.goalRepository = goalRepository;
        this.memberServiceClient = memberServiceClient;
    }

    @Transactional
    public GoalViewResDto create(GoalCreateReqDto dto, UUID requesterId, UUID companyId) {
        validateOwner(dto.getOwnerType(), dto.getOwnerId(), requesterId);
        validateCycleDates(dto.getCycle(), dto.getCycleStartDate(), dto.getCycleEndDate());

        Goal goal = Goal.builder()
                .companyId(companyId)
                .ownerType(dto.getOwnerType())
                .ownerId(dto.getOwnerId())
                .alignedOrgGoalId(dto.getAlignedOrgGoalId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .cycle(dto.getCycle())
                .cycleStartDate(dto.getCycleStartDate())
                .cycleEndDate(dto.getCycleEndDate())
                .weightPct(dto.getOwnerType() == GoalOwnerType.ORGANIZATION ? 0 : dto.getWeightPct())
                .gradeSCriteria(dto.getOwnerType() == GoalOwnerType.ORGANIZATION ? blankToNull(dto.getGradeS()) : null)
                .gradeACriteria(dto.getOwnerType() == GoalOwnerType.ORGANIZATION ? blankToNull(dto.getGradeA()) : null)
                .gradeBCriteria(dto.getOwnerType() == GoalOwnerType.ORGANIZATION ? blankToNull(dto.getGradeB()) : null)
                .gradeCCriteria(dto.getOwnerType() == GoalOwnerType.ORGANIZATION ? blankToNull(dto.getGradeC()) : null)
                .visibleTeamIds(dto.getVisibleTeamIds() != null ? dto.getVisibleTeamIds() : new ArrayList<>())
                .participantMemberIds(dto.getParticipantMemberIds() != null ? dto.getParticipantMemberIds() : new ArrayList<>())
                .build();

        if (dto.getOwnerType() == GoalOwnerType.ORGANIZATION) {
            goal.activateObjective();
        } else {
            applyCriteriaForMemberGoal(goal, dto, companyId);
        }

        return GoalViewMapper.toView(goalRepository.save(goal));
    }

    @Transactional
    public GoalViewResDto update(UUID goalId, GoalUpdateReqDto dto, UUID requesterId, UUID companyId, boolean canManageOrgGoal) {
        Goal goal = mustGet(goalId, companyId);

        if (goal.getOwnerType() == GoalOwnerType.ORGANIZATION) {
            ensureCanManageOrgGoal(canManageOrgGoal);
            updateOrganizationGoal(goal, dto);
            return GoalViewMapper.toView(goal);
        }

        ensureDraftOwnerGoal(goal, requesterId);

        if (dto.getTitle() != null || dto.getDescription() != null) {
            goal.updateContent(
                    dto.getTitle() != null ? dto.getTitle() : goal.getTitle(),
                    dto.getDescription() != null ? dto.getDescription() : goal.getDescription()
            );
        }
        if (dto.getWeightPct() != null) {
            goal.updateWeight(dto.getWeightPct());
        }
        if (dto.getAlignedOrgGoalId() != null) {
            goal.updateAlignment(dto.getAlignedOrgGoalId());
            reapplyCriteriaForAlignment(goal, dto, companyId);
        } else if (hasAnyCriteria(dto)) {
            if (goal.getAlignedOrgGoalId() != null) {
                Goal orgGoal = getActiveOrgGoal(goal.getAlignedOrgGoalId(), companyId);
                if (orgGoal.hasGradeCriteria()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Inherited grade criteria cannot be edited.");
                }
            }
            validateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC(), "personal goal");
            goal.updateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC());
        }
        if (dto.getParticipantMemberIds() != null) {
            goal.updateParticipants(dto.getParticipantMemberIds());
        }
        return GoalViewMapper.toView(goal);
    }

    @Transactional
    public void delete(UUID goalId, UUID requesterId, UUID companyId, boolean canManageOrgGoal) {
        Goal goal = mustGet(goalId, companyId);
        if (goal.getOwnerType() == GoalOwnerType.ORGANIZATION) {
            ensureCanManageOrgGoal(canManageOrgGoal);
        } else {
            ensureDraftOwnerGoal(goal, requesterId);
        }
        goalRepository.delete(goal);
    }

    @Transactional
    public GoalViewResDto cancelMine(UUID goalId, UUID requesterId, UUID companyId, boolean canManageOrgGoal) {
        Goal goal = mustGet(goalId, companyId);
        if (goal.getOwnerType() == GoalOwnerType.MEMBER && !goal.getOwnerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can cancel only your own goal.");
        }
        if (goal.getOwnerType() == GoalOwnerType.ORGANIZATION) {
            ensureCanManageOrgGoal(canManageOrgGoal);
        }
        goal.cancel();
        return GoalViewMapper.toView(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalKrResDto> listMyGoals(UUID memberId, UUID companyId, GoalStatus statusFilter) {
        List<Goal> goals = statusFilter == null
                ? goalRepository.findByCompanyIdAndOwnerIdOrderByCycleStartDateDesc(companyId, memberId)
                : goalRepository.findByCompanyIdAndOwnerIdAndStatus(companyId, memberId, statusFilter);
        return mapMemberGoals(goals, companyId);
    }

    @Transactional(readOnly = true)
    public List<GoalViewResDto> listCompanyGoals(UUID requesterId, UUID companyId, KpiCycle cycle, boolean hasEvalRead) {
        return goalRepository.findByCompanyId(companyId).stream()
                .filter(goal -> cycle == null || goal.getCycle() == cycle)
                .map(GoalViewMapper::toView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GoalCycleResDto> listOrganizationGoalCycles(UUID companyId) {
        return goalRepository.findByCompanyIdAndOwnerTypeOrderByCycleStartDateDesc(
                        companyId, GoalOwnerType.ORGANIZATION)
                .stream()
                .filter(goal -> goal.getStatus() == GoalStatus.ACTIVE)
                .collect(Collectors.groupingBy(
                        goal -> goal.getCycle() + "|" + goal.getCycleStartDate() + "|" + goal.getCycleEndDate(),
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(goals -> {
                    Goal first = goals.get(0);
                    return new GoalCycleResDto(
                            first.getCycle(),
                            first.getCycleStartDate(),
                            first.getCycleEndDate(),
                            goals.size()
                    );
                })
                .sorted((a, b) -> b.cycleStartDate().compareTo(a.cycleStartDate()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GoalObjectiveResDto> listMyObjectives(UUID requesterId, UUID companyId, KpiCycle cycle) {
        UUID requesterOrgId = memberServiceClient.findOrganizationId(companyId, requesterId);
        if (requesterOrgId == null) {
            return Collections.emptyList();
        }
        return listOrgObjectives(requesterId, requesterOrgId, companyId, cycle);
    }

    @Transactional(readOnly = true)
    public List<GoalKrResDto> listOrgGoals(UUID callerId, UUID orgId, UUID companyId) {
        List<UUID> memberIds = memberServiceClient.findMemberIdsByOrgId(companyId, orgId);
        if (memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapMemberGoals(goalRepository.findOrgScope(companyId, GoalOwnerType.MEMBER, memberIds), companyId);
    }

    @Transactional(readOnly = true)
    public List<GoalObjectiveResDto> listOrgObjectives(UUID callerId, UUID orgId, UUID companyId, KpiCycle cycle) {
        return goalRepository.findByCompanyIdAndOwnerTypeAndOwnerIdOrderByCycleStartDateDesc(
                        companyId, GoalOwnerType.ORGANIZATION, orgId)
                .stream()
                .filter(goal -> cycle == null || goal.getCycle() == cycle)
                .map(GoalObjectiveResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GoalViewResDto get(UUID goalId, UUID requesterId, UUID companyId, boolean hasEvalRead) {
        Goal goal = mustGet(goalId, companyId);
        if (!canRead(goal, requesterId, companyId, hasEvalRead)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You do not have permission to read this goal.");
        }
        return GoalViewMapper.toView(goal);
    }

    @Transactional(readOnly = true)
    public GoalViewResDto getForOwnerFlow(UUID goalId, UUID requesterId, UUID companyId) {
        return get(goalId, requesterId, companyId, false);
    }

    @Transactional
    public GoalViewResDto createForOwnerFlow(GoalCreateReqDto dto, UUID requesterId, UUID companyId) {
        return create(dto, requesterId, companyId);
    }

    @Transactional
    public GoalViewResDto activateForOwnerFlow(UUID goalId, UUID requesterId, UUID companyId) {
        Goal goal = mustGet(goalId, companyId);
        goal.activate(requesterId);
        return GoalViewMapper.toView(goal);
    }

    @Transactional(readOnly = true)
    public Goal requireReadable(UUID goalId, UUID requesterId, UUID companyId) {
        Goal goal = mustGet(goalId, companyId);
        if (!canRead(goal, requesterId, companyId, false)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You do not have permission to read this goal.");
        }
        return goal;
    }

    private void applyCriteriaForMemberGoal(Goal goal, GoalCreateReqDto dto, UUID companyId) {
        if (goal.getAlignedOrgGoalId() != null) {
            Goal orgGoal = getActiveOrgGoal(goal.getAlignedOrgGoalId(), companyId);
            if (orgGoal.hasGradeCriteria()) {
                goal.inheritCriteriaFrom(orgGoal);
                return;
            }
        }
        validateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC(), "personal goal");
        goal.updateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC());
    }

    private void reapplyCriteriaForAlignment(Goal goal, GoalUpdateReqDto dto, UUID companyId) {
        Goal orgGoal = getActiveOrgGoal(goal.getAlignedOrgGoalId(), companyId);
        if (orgGoal.hasGradeCriteria()) {
            goal.inheritCriteriaFrom(orgGoal);
            return;
        }
        validateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC(), "personal goal");
        goal.updateGradeCriteria(dto.getGradeS(), dto.getGradeA(), dto.getGradeB(), dto.getGradeC());
    }

    private void updateOrganizationGoal(Goal goal, GoalUpdateReqDto dto) {
        if (dto.getOwnerId() != null) {
            goal.updateObjectiveOwner(dto.getOwnerId());
        }
        if (dto.getTitle() != null || dto.getDescription() != null) {
            goal.updateObjectiveContent(
                    dto.getTitle() != null ? dto.getTitle() : goal.getTitle(),
                    dto.getDescription() != null ? dto.getDescription() : goal.getDescription()
            );
        }
        if (hasAnyCriteria(dto)) {
            goal.updateGradeCriteria(
                    dto.getGradeS() != null ? dto.getGradeS() : goal.getGradeSCriteria(),
                    dto.getGradeA() != null ? dto.getGradeA() : goal.getGradeACriteria(),
                    dto.getGradeB() != null ? dto.getGradeB() : goal.getGradeBCriteria(),
                    dto.getGradeC() != null ? dto.getGradeC() : goal.getGradeCCriteria()
            );
        }
        if (dto.getParticipantMemberIds() != null) {
            goal.updateObjectiveParticipants(dto.getParticipantMemberIds());
        }
    }

    private List<GoalKrResDto> mapMemberGoals(List<Goal> goals, UUID companyId) {
        Map<UUID, Goal> objectiveMap = loadObjectiveMap(goals, companyId);
        return goals.stream()
                .filter(goal -> goal.getOwnerType() == GoalOwnerType.MEMBER)
                .map(goal -> GoalKrResDto.from(goal, objectiveMap.get(goal.getAlignedOrgGoalId())))
                .collect(Collectors.toList());
    }

    private Map<UUID, Goal> loadObjectiveMap(List<Goal> goals, UUID companyId) {
        Set<UUID> objectiveIds = goals.stream()
                .map(Goal::getAlignedOrgGoalId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (objectiveIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return goalRepository.findAllById(objectiveIds).stream()
                .filter(goal -> companyId.equals(goal.getCompanyId()))
                .collect(Collectors.toMap(Goal::getGoalId, goal -> goal));
    }

    private boolean canRead(Goal goal, UUID requesterId, UUID companyId, boolean hasEvalRead) {
        if (hasEvalRead) {
            return true;
        }
        if (goal.getOwnerType() == GoalOwnerType.MEMBER) {
            return goal.getOwnerId().equals(requesterId)
                    || (goal.getParticipantMemberIds() != null && goal.getParticipantMemberIds().contains(requesterId));
        }
        UUID requesterOrgId = memberServiceClient.findOrganizationId(companyId, requesterId);
        return requesterOrgId != null && requesterOrgId.equals(goal.getOwnerId());
    }

    private Goal getActiveOrgGoal(UUID goalId, UUID companyId) {
        Goal orgGoal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Aligned organization goal was not found."));
        if (!companyId.equals(orgGoal.getCompanyId())
                || orgGoal.getOwnerType() != GoalOwnerType.ORGANIZATION
                || orgGoal.getStatus() != GoalStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Aligned organization goal must be active and in the same company.");
        }
        return orgGoal;
    }

    private void ensureDraftOwnerGoal(Goal goal, UUID requesterId) {
        if (goal.getStatus() != GoalStatus.DRAFT || goal.getGoalApprovalStatus() == GoalApprovalStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only draft personal goals can be edited.");
        }
        if (!goal.getOwnerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can edit only your own goal.");
        }
    }

    private void validateOwner(GoalOwnerType ownerType, UUID ownerId, UUID requesterId) {
        if (ownerType == GoalOwnerType.MEMBER && !ownerId.equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can create personal goals only for yourself.");
        }
    }

    private void ensureCanManageOrgGoal(boolean canManageOrgGoal) {
        if (!canManageOrgGoal) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You do not have permission to manage organization goals.");
        }
    }

    private void validateCycleDates(KpiCycle cycle, LocalDate start, LocalDate end) {
        if (cycle == null || start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Goal cycle dates are invalid.");
        }
        CycleKeyResolver.resolve(cycle, start);
    }

    private void validateGradeCriteria(String s, String a, String b, String c, String label) {
        if (isBlank(s) || isBlank(a) || isBlank(b) || isBlank(c)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "S/A/B/C grade criteria are required for " + label + ".");
        }
    }

    private boolean hasAnyCriteria(GoalUpdateReqDto dto) {
        return dto.getGradeS() != null || dto.getGradeA() != null || dto.getGradeB() != null || dto.getGradeC() != null;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Goal mustGet(UUID goalId, UUID companyId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Goal not found."));
        if (!companyId.equals(goal.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Goal belongs to another company.");
        }
        return goal;
    }
}
