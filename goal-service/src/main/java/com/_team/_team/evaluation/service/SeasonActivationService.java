package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import com._team._team.evaluation.domain.enums.EvalType;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.exception.SeasonActivationBlockedException;
import com._team._team.evaluation.repository.EvaluationCalibrationRepository;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.util.GoalSnapshot;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.GoalApprovalBundle;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.feignclients.MemberServiceClient;
import com._team._team.goal.feignclients.dto.MemberOrgContextDto;
import com._team._team.goal.repository.GoalApprovalBundleRepository;
import com._team._team.goal.repository.GoalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SeasonActivationService {

    private final GoalRepository goalRepository;
    private final GoalApprovalBundleRepository bundleRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationCalibrationRepository calibrationRepository;
    private final MemberServiceClient memberServiceClient;
    private final ObjectMapper objectMapper;

    public SeasonActivationService(GoalRepository goalRepository,
                                   GoalApprovalBundleRepository bundleRepository,
                                   EvaluationResponseRepository responseRepository,
                                   EvaluationCalibrationRepository calibrationRepository,
                                   MemberServiceClient memberServiceClient,
                                   ObjectMapper objectMapper) {
        this.goalRepository = goalRepository;
        this.bundleRepository = bundleRepository;
        this.responseRepository = responseRepository;
        this.calibrationRepository = calibrationRepository;
        this.memberServiceClient = memberServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void activate(EvaluationSeason season, List<TargetSpec> targetSpecs) {
        if (season.getStatus() != SeasonStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only draft seasons can be activated.");
        }
        if (season.getTargetCycle() == null || season.getTargetCycleStart() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "targetCycle and targetCycleStart are required.");
        }
        if (targetSpecs == null || targetSpecs.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Season activation requires at least one target.");
        }

        List<UUID> shortage = new ArrayList<>();
        List<UUID> pending = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        List<UUID> inactive = new ArrayList<>();
        List<TargetSpec> eligibleSpecs = new ArrayList<>();

        for (TargetSpec spec : targetSpecs) {
            UUID memberId = spec.getTargetMemberId();
            if (!isEvaluableActiveMember(season, memberId)) {
                inactive.add(memberId);
                continue;
            }
            eligibleSpecs.add(spec);
            List<Goal> activeGoals = findActiveKrsForSeason(memberId, season);
            if (activeGoals.isEmpty()) {
                missing.add(memberId);
                continue;
            }

            Map<UUID, Goal> objectiveMap = loadObjectives(activeGoals);
            if (objectiveMap.size() != activeGoals.size()) {
                missing.add(memberId);
                continue;
            }
            validateObjectiveRubrics(activeGoals, objectiveMap);

            int weightSum = activeGoals.stream().mapToInt(Goal::getWeightPct).sum();
            if (weightSum != 100) {
                shortage.add(memberId);
            }

            List<GoalApprovalBundle> pendingBundles = bundleRepository
                    .findByRequestedByAndCompanyIdAndStatus(memberId, season.getCompanyId(), BundleApprovalStatus.PENDING);
            if (!pendingBundles.isEmpty()) {
                pending.add(memberId);
            }
        }

        if (eligibleSpecs.isEmpty() || !inactive.isEmpty() || !shortage.isEmpty() || !pending.isEmpty() || !missing.isEmpty()) {
            throw new SeasonActivationBlockedException(inactive, shortage, pending, missing);
        }

        for (TargetSpec spec : eligibleSpecs) {
            UUID memberId = spec.getTargetMemberId();
            List<Goal> activeGoals = findActiveKrsForSeason(memberId, season);
            Map<UUID, Goal> objectiveMap = loadObjectives(activeGoals);

            activeGoals.forEach(Goal::completeForEvaluation);
            String snapshotJson = serialize(GoalSnapshot.of(activeGoals, objectiveMap));

            UUID leadId = spec.getLeadEvaluatorId() != null
                    ? spec.getLeadEvaluatorId()
                    : memberServiceClient.findDirectManagerId(season.getCompanyId(), memberId);
            if (leadId == null) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Lead evaluator could not be determined. memberId=" + memberId
                );
            }

            EvaluationResponse response = EvaluationResponse.builder()
                    .companyId(season.getCompanyId())
                    .group(spec.getGroupRef())
                    .targetMemberId(memberId)
                    .evaluatorId(leadId)
                    .evaluationType(EvalType.SELF)
                    .stage(EvaluationStage.SELF_PENDING)
                    .goalSnapshotJson(snapshotJson)
                    .build();
            EvaluationResponse saved = responseRepository.save(response);

            calibrationRepository.save(EvaluationCalibration.builder()
                    .responseId(saved.getResponseId())
                    .evaluatorId(leadId)
                    .role(CalibrationRole.LEAD)
                    .build());

            if (spec.getAssistantEvaluatorIds() != null) {
                for (UUID assistantId : spec.getAssistantEvaluatorIds()) {
                    if (assistantId.equals(leadId)) {
                        continue;
                    }
                    calibrationRepository.save(EvaluationCalibration.builder()
                            .responseId(saved.getResponseId())
                            .evaluatorId(assistantId)
                            .role(CalibrationRole.ASSISTANT)
                            .build());
                }
            }
        }

        season.openSelfEval();
    }

    private boolean isEvaluableActiveMember(EvaluationSeason season, UUID memberId) {
        MemberOrgContextDto context = memberServiceClient.getOrgContext(memberId);
        if (context == null) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(context.getMemberStatus())) {
            return false;
        }
        if (context.getJoinDate() != null && season.getTargetCycleStart() != null
                && context.getJoinDate().isAfter(season.getTargetCycleStart())) {
            return false;
        }
        return true;
    }

    private List<Goal> findActiveKrsForSeason(UUID memberId, EvaluationSeason season) {
        return goalRepository.findByOwnerIdAndCycleAndCycleStartDateAndStatusIn(
                        memberId,
                        season.getTargetCycle(),
                        season.getTargetCycleStart(),
                        EnumSet.of(GoalStatus.ACTIVE))
                .stream()
                .filter(goal -> goal.getOwnerType() == GoalOwnerType.MEMBER)
                .toList();
    }

    private String serialize(GoalSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize goal snapshot.");
        }
    }

    private Map<UUID, Goal> loadObjectives(List<Goal> goals) {
        Map<UUID, Goal> objectiveMap = new HashMap<>();
        for (Goal goal : goals) {
            UUID objectiveId = goal.getAlignedOrgGoalId();
            if (objectiveId == null) {
                continue;
            }
            goalRepository.findById(objectiveId).ifPresent(objective -> objectiveMap.put(objectiveId, objective));
        }
        return objectiveMap;
    }

    private void validateObjectiveRubrics(List<Goal> goals, Map<UUID, Goal> objectiveMap) {
        for (Goal goal : goals) {
            Goal objective = objectiveMap.get(goal.getAlignedOrgGoalId());
            if (objective == null || objective.getOwnerType() != GoalOwnerType.ORGANIZATION) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Each KR must have a valid aligned objective. goalId=" + goal.getGoalId()
                );
            }
            if (isBlank(goal.getGradeSCriteria())
                    || isBlank(goal.getGradeACriteria())
                    || isBlank(goal.getGradeBCriteria())
                    || isBlank(goal.getGradeCCriteria())) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Personal goal grade criteria are incomplete. goalId=" + goal.getGoalId()
                );
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class TargetSpec {
        private final com._team._team.evaluation.domain.EvaluationGroup groupRef;
        private final UUID targetMemberId;
        private final UUID leadEvaluatorId;
        private final List<UUID> assistantEvaluatorIds;

        public TargetSpec(com._team._team.evaluation.domain.EvaluationGroup groupRef,
                          UUID targetMemberId,
                          UUID leadEvaluatorId,
                          List<UUID> assistantEvaluatorIds) {
            this.groupRef = groupRef;
            this.targetMemberId = targetMemberId;
            this.leadEvaluatorId = leadEvaluatorId;
            this.assistantEvaluatorIds = assistantEvaluatorIds == null ? List.of() : assistantEvaluatorIds;
        }

        public com._team._team.evaluation.domain.EvaluationGroup getGroupRef() {
            return groupRef;
        }

        public UUID getTargetMemberId() {
            return targetMemberId;
        }

        public UUID getLeadEvaluatorId() {
            return leadEvaluatorId;
        }

        public List<UUID> getAssistantEvaluatorIds() {
            return assistantEvaluatorIds;
        }
    }
}
