package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationGroup;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.converter.EvaluatorMapping;
import com._team._team.evaluation.domain.enums.EvalType;
import com._team._team.evaluation.repository.EvaluationGroupRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.goal.repository.GoalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EvaluationGroupTranslator
 *
 *  기존 EvaluationGroup 구조 (targetMemberIdsJson + List&lt;EvaluatorMapping&gt;) 를
 *  SeasonActivationService.TargetSpec 리스트로 변환.
 *
 *  매핑 규칙:
 *   - 한 시즌의 모든 EvaluationGroup 을 합쳐서 평탄화
 *   - 각 (group, targetMemberId) 쌍이 하나의 TargetSpec 이 됨
 *   - LEAD = EvaluatorMapping(targetMemberId, evaluationType=DOWNWARD).evaluatorId
 *            (DOWNWARD = 상사가 부하를 평가 → 상위자가 LEAD)
 *            DOWNWARD 매핑이 없으면 null → SeasonActivationService 에서 직속 조직장 fallback
 *   - ASSISTANT = UPWARD/PEER 매핑된 evaluatorId 들
 *   - SELF 매핑은 평가 자체와 별개라 무시 (자기평가는 항상 본인이 함)
 */
@Component
public class EvaluationGroupTranslator {

    private final EvaluationGroupRepository groupRepository;
    private final EvaluationSeasonRepository seasonRepository;
    private final GoalRepository goalRepository;
    private final ObjectMapper objectMapper;

    public EvaluationGroupTranslator(EvaluationGroupRepository groupRepository,
                                     EvaluationSeasonRepository seasonRepository,
                                     GoalRepository goalRepository,
                                     ObjectMapper objectMapper) {
        this.groupRepository = groupRepository;
        this.seasonRepository = seasonRepository;
        this.goalRepository = goalRepository;
        this.objectMapper = objectMapper;
    }

    public List<SeasonActivationService.TargetSpec> translate(UUID seasonId) {
        List<EvaluationGroup> groups = groupRepository.findBySeason_SeasonId(seasonId);
        if (groups.isEmpty()) {
            groups = List.of(createAutoGroup(seasonId));
        }

        List<SeasonActivationService.TargetSpec> specs = new ArrayList<>();
        for (EvaluationGroup g : groups) {
            Set<UUID> mergedTargets = new HashSet<>(parseUuidList(g.getTargetMemberIdsJson()));
            if (g.getSeason() != null) {
                mergedTargets.addAll(goalRepository.findMemberIdsWithActiveGoalByCycle(
                        g.getCompanyId(),
                        g.getSeason().getTargetCycle(),
                        g.getSeason().getTargetCycleStart()
                ));
            }
            List<UUID> targetMembers = new ArrayList<>(mergedTargets);
            if (targetMembers.isEmpty()) continue;

            Map<UUID, UUID> leadMap = new HashMap<>();          // target → LEAD evaluator
            Map<UUID, Set<UUID>> assistantMap = new HashMap<>(); // target → ASSISTANT evaluators

            for (EvaluatorMapping m : safe(g.getEvaluatorMaps())) {
                if (m.getTargetMemberId() == null || m.getEvaluatorId() == null) continue;
                UUID t = m.getTargetMemberId();
                EvalType type = m.getEvaluationType();
                if (type == null) continue;

                if (type == EvalType.DOWNWARD) {
                    // 한 명만 LEAD — 마지막 매핑 우선
                    leadMap.put(t, m.getEvaluatorId());
                } else if (type == EvalType.UPWARD || type == EvalType.PEER) {
                    assistantMap
                            .computeIfAbsent(t, k -> new HashSet<>())
                            .add(m.getEvaluatorId());
                }
                // SELF 는 무시
            }

            for (UUID target : targetMembers) {
                UUID leadId = leadMap.get(target); // null 가능 → activation 에서 fallback
                List<UUID> assistants = new ArrayList<>(assistantMap.getOrDefault(target, Set.of()));
                if (leadId != null) {
                    assistants.remove(leadId); // LEAD 가 ASSISTANT 에도 있으면 제외
                }
                specs.add(new SeasonActivationService.TargetSpec(g, target, leadId, assistants));
            }
        }
        return specs;
    }

    private EvaluationGroup createAutoGroup(UUID seasonId) {
        EvaluationSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 시즌을 찾을 수 없습니다."));
        List<UUID> targetMembers = goalRepository.findMemberIdsWithActiveGoalByCycle(
                season.getCompanyId(),
                season.getTargetCycle(),
                season.getTargetCycleStart()
        );
        if (targetMembers.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "해당 시즌 목표를 보유한 ACTIVE 구성원이 없습니다.");
        }
        try {
            return groupRepository.save(EvaluationGroup.builder()
                    .companyId(season.getCompanyId())
                    .season(season)
                    .name("자동 로드 평가 대상자")
                    .evaluationTypesJson(objectMapper.writeValueAsString(List.of("SELF", "DOWNWARD")))
                    .targetMemberIdsJson(objectMapper.writeValueAsString(targetMembers))
                    .evaluatorMaps(buildSelfMappings(targetMembers))
                    .build());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "자동 평가 대상자 그룹 생성에 실패했습니다.");
        }
    }

    private List<EvaluatorMapping> buildSelfMappings(List<UUID> targetMembers) {
        List<EvaluatorMapping> mappings = new ArrayList<>();
        for (UUID target : targetMembers) {
            mappings.add(EvaluatorMapping.builder()
                    .targetMemberId(target)
                    .evaluatorId(target)
                    .evaluationType(EvalType.SELF)
                    .build());
        }
        return mappings;
    }

    // -----------------------------------------------------------------
    private List<UUID> parseUuidList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "targetMemberIdsJson 역직렬화 실패");
        }
    }

    private List<EvaluatorMapping> safe(List<EvaluatorMapping> list) {
        return list == null ? List.of() : list;
    }
}
