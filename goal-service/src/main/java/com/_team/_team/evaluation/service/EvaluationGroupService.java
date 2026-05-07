package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationDesign;
import com._team._team.evaluation.domain.EvaluationGroup;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.converter.DesignQuestion;
import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.converter.EvaluatorMapping;
import com._team._team.evaluation.domain.converter.GradeConfig;
import com._team._team.evaluation.domain.enums.EvalType;
import com._team._team.evaluation.domain.enums.SectionType;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.dto.reqdto.EvaluatorMapUpdateReqDto;
import com._team._team.evaluation.dto.reqdto.GroupCreateReqDto;
import com._team._team.evaluation.dto.reqdto.GroupUpdateReqDto;
import com._team._team.evaluation.dto.resdto.EvaluatorMappingResDto;
import com._team._team.evaluation.dto.resdto.GroupResDto;
import com._team._team.evaluation.feignclients.MemberServiceClient;
import com._team._team.evaluation.feignclients.dto.MemberMinimalProfileDto;
import com._team._team.evaluation.repository.EvaluationDesignRepository;
import com._team._team.evaluation.repository.EvaluationGroupRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.goal.repository.GoalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationGroupService {

    private final EvaluationGroupRepository groupRepository;
    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationDesignRepository designRepository;
    private final GoalRepository goalRepository;
    private final ObjectMapper objectMapper;
    private final MemberServiceClient memberServiceClient;

    public EvaluationGroupService(EvaluationGroupRepository groupRepository,
                                   EvaluationSeasonRepository seasonRepository,
                                   EvaluationDesignRepository designRepository,
                                   GoalRepository goalRepository,
                                   ObjectMapper objectMapper,
                                   MemberServiceClient memberServiceClient) {
        this.groupRepository = groupRepository;
        this.seasonRepository = seasonRepository;
        this.designRepository = designRepository;
        this.goalRepository = goalRepository;
        this.objectMapper = objectMapper;
        this.memberServiceClient = memberServiceClient;
    }

    /**
     * 평가자 자동 지정.
     *
     * 각 대상자 × (그룹에 설정된 평가 유형) 조합마다 member-service 후보 조회 API 로 후보를 얻어
     * evaluatorMaps 를 구성한다.
     *  - SELF: 항상 target = evaluator
     *  - DOWNWARD/UPWARD/PEER: 후보 중 첫 번째 (조직 + 직급 조건 일치) 를 배정. 후보 0명이면 건너뜀
     *
     * 기존 evaluatorMaps 는 덮어씀.
     * 반환값: 자동 배정 결과가 반영된 GroupResDto.
     */
    @Transactional
    public GroupResDto autoAssignEvaluators(UUID seasonId, UUID groupId, UUID companyId, String basis) {
        EvaluationGroup group = findGroupOrThrow(groupId, companyId);

        List<UUID> targets;
        List<String> evalTypes;
        try {
            targets = group.getTargetMemberIdsJson() != null
                    ? objectMapper.readValue(group.getTargetMemberIdsJson(),
                            new TypeReference<List<UUID>>() {})
                    : List.of();
            evalTypes = group.getEvaluationTypesJson() != null
                    ? objectMapper.readValue(group.getEvaluationTypesJson(),
                            new TypeReference<List<String>>() {})
                    : List.of();
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "그룹 설정 JSON 이 올바르지 않습니다.");
        }

        // [D-3] 타입 세이프 List<EvaluatorMapping> 로 직접 구성. 더 이상 수동 JSON 작성 불필요.
        List<EvaluatorMapping> newMaps = new ArrayList<>();
        for (UUID targetId : targets) {
            for (String rawType : evalTypes) {
                String type = rawType == null ? "" : rawType.trim().toUpperCase();
                EvalType evalType;
                try {
                    evalType = EvalType.valueOf(type);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (evalType == EvalType.SELF) {
                    newMaps.add(EvaluatorMapping.builder()
                            .targetMemberId(targetId)
                            .evaluatorId(targetId)
                            .evaluationType(evalType)
                            .build());
                    continue;
                }
                List<UUID> candidates;
                try {
                    candidates = memberServiceClient.getCandidatesForEvaluator(companyId.toString(), targetId, type);
                } catch (Exception e) {
                    candidates = List.of();
                }
                if (candidates == null || candidates.isEmpty()) continue;
                UUID evaluatorId = candidates.get(0);
                newMaps.add(EvaluatorMapping.builder()
                        .targetMemberId(targetId)
                        .evaluatorId(evaluatorId)
                        .evaluationType(evalType)
                        .build());
            }
        }

        group.updateEvaluatorMaps(newMaps);
        syncGroupScopeFieldsFromMaps(group, newMaps);
        groupRepository.save(group);
        return toGroupResDto(group);
    }

    @Transactional
    public List<GroupResDto> listGroups(UUID seasonId, UUID companyId) {
        EvaluationSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 시즌을 찾을 수 없습니다."));
        if (!season.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "평가 시즌을 찾을 수 없습니다.");
        }

        List<EvaluationGroup> groups = groupRepository.findBySeason_SeasonId(seasonId);
        if (groups.isEmpty()) {
            groups = autoCreateGroupFromActiveGoals(season);
        }

        return groups
                .stream().map(this::toGroupResDto).collect(Collectors.toList());
    }

    @Transactional
    public List<GroupResDto> listGroups(UUID seasonId, UUID companyId, EvaluationAccessScopeService.AccessScope scope) {
        List<GroupResDto> groups = listGroups(seasonId, companyId);
        if (scope == null || scope.companyWide()) {
            return groups;
        }
        return groups.stream()
                .map(group -> filterGroupForScope(group, scope))
                .filter(group -> hasVisibleTargets(group) || hasVisibleMappings(group))
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupResDto createGroup(UUID seasonId, UUID companyId, GroupCreateReqDto dto) {
        throw new BusinessException(HttpStatus.BAD_REQUEST,
                "평가 대상자는 목표 기간의 승인 완료 개인 목표 기준으로 자동 생성됩니다. 수동 그룹 생성은 지원하지 않습니다.");
    }

    private List<EvaluationGroup> autoCreateGroupFromActiveGoals(EvaluationSeason season) {
        List<UUID> targetMemberIds = goalRepository.findMemberIdsWithActiveGoalByCycle(
                season.getCompanyId(),
                season.getTargetCycle(),
                season.getTargetCycleStart()
        );
        if (targetMemberIds.isEmpty()) {
            return List.of();
        }

        try {
            EvaluationGroup group = EvaluationGroup.builder()
                    .companyId(season.getCompanyId())
                    .season(season)
                    .name("자동 로드 평가 대상자")
                    .evaluationTypesJson(objectMapper.writeValueAsString(List.of("SELF", "DOWNWARD")))
                    .targetMemberIdsJson(objectMapper.writeValueAsString(targetMemberIds))
                    .evaluatorMaps(buildSelfMappings(targetMemberIds))
                    .design(findOrCreateDefaultGoalPolicy(season.getCompanyId()))
                    .build();
            return List.of(groupRepository.save(group));
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "평가 대상자 자동 로드에 실패했습니다.");
        }
    }

    private List<EvaluatorMapping> buildSelfMappings(List<UUID> targetMemberIds) {
        return targetMemberIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(targetId -> EvaluatorMapping.builder()
                        .targetMemberId(targetId)
                        .evaluatorId(targetId)
                        .evaluationType(EvalType.SELF)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupResDto updateGroup(UUID seasonId, UUID groupId, UUID companyId, GroupUpdateReqDto dto) {
        EvaluationGroup group = findGroupOrThrow(groupId, companyId);
        if (group.getSeason() != null && group.getSeason().getStatus() != SeasonStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "시즌 시작 이후에는 그룹을 수정할 수 없습니다.");
        }

        String evalTypesJson = group.getEvaluationTypesJson();
        String targetMemberIdsJson = group.getTargetMemberIdsJson();
        try {
            if (dto.getEvaluationTypes() != null) evalTypesJson = objectMapper.writeValueAsString(dto.getEvaluationTypes());
            if (dto.getTargetMemberIds() != null) targetMemberIdsJson = objectMapper.writeValueAsString(dto.getTargetMemberIds());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "JSON 변환에 실패했습니다.");
        }

        if (dto.getDesignId() != null) {
            EvaluationDesign design = designRepository.findById(dto.getDesignId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 설계를 찾을 수 없습니다."));
            group.assignDesign(design);
        }

        group.update(
                dto.getName() != null ? dto.getName() : group.getName(),
                evalTypesJson,
                targetMemberIdsJson,
                group.getEvaluatorMaps()
        );

        return toGroupResDto(group);
    }

    @Transactional
    public void deleteGroup(UUID groupId, UUID companyId) {
        EvaluationGroup group = findGroupOrThrow(groupId, companyId);
        groupRepository.delete(group);
    }

    @Transactional
    public GroupResDto updateEvaluatorMaps(UUID groupId, UUID companyId, List<EvaluatorMapUpdateReqDto.EvaluatorMapItemReqDto> evaluatorMaps) {
        EvaluationGroup group = findGroupOrThrow(groupId, companyId);
        List<EvaluatorMapping> maps = evaluatorMaps == null
                ? new ArrayList<>()
                : evaluatorMaps.stream()
                .filter(Objects::nonNull)
                .map(m -> EvaluatorMapping.builder()
                        .targetMemberId(m.getTargetMemberId())
                        .evaluatorId(m.getEvaluatorId())
                        .evaluationType(m.getEvaluationType())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        group.updateEvaluatorMaps(maps);
        syncGroupScopeFieldsFromMaps(group, maps);
        groupRepository.save(group);
        return toGroupResDto(group);
    }

    private EvaluationGroup findGroupOrThrow(UUID groupId, UUID companyId) {
        EvaluationGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 그룹을 찾을 수 없습니다."));
        if (!group.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "평가 그룹을 찾을 수 없습니다.");
        }
        return group;
    }

    private EvaluationDesign findOrCreateDefaultGoalPolicy(UUID companyId) {
        return designRepository.findFirstByCompanyIdAndDefaultTemplateTrue(companyId)
                .or(() -> designRepository.findFirstByCompanyIdOrderByCreatedAtAsc(companyId))
                .orElseGet(() -> designRepository.save(EvaluationDesign.builder()
                        .companyId(companyId)
                        .name("기본 목표 평가 정책")
                        .sections(defaultGoalSections())
                        .gradeConfig(defaultRelativeGradeConfig())
                        .templateVersion(1)
                        .defaultTemplate(true)
                        .build()));
    }

    private List<EvaluationSection> defaultGoalSections() {
        DesignQuestion goalGrade = DesignQuestion.builder()
                .id("goal-grade")
                .type("grade")
                .title("목표별 등급")
                .required(true)
                .weight(BigDecimal.valueOf(100))
                .build();
        EvaluationSection goalSection = EvaluationSection.builder()
                .sectionId("goal-performance")
                .title("목표 성과")
                .type(SectionType.MANUAL)
                .weight(BigDecimal.valueOf(100))
                .questions(new ArrayList<>(List.of(goalGrade)))
                .build();
        return new ArrayList<>(List.of(goalSection));
    }

    private GradeConfig defaultRelativeGradeConfig() {
        return GradeConfig.builder()
                .type("RELATIVE")
                .grades(new ArrayList<>(List.of(
                        GradeConfig.GradeBand.builder()
                                .label("S")
                                .minScore(BigDecimal.valueOf(92))
                                .maxScore(BigDecimal.valueOf(100))
                                .color("#2563eb")
                                .build(),
                        GradeConfig.GradeBand.builder()
                                .label("A")
                                .minScore(BigDecimal.valueOf(78))
                                .maxScore(BigDecimal.valueOf(91.99))
                                .color("#16a34a")
                                .build(),
                        GradeConfig.GradeBand.builder()
                                .label("B")
                                .minScore(BigDecimal.valueOf(63))
                                .maxScore(BigDecimal.valueOf(77.99))
                                .color("#f59e0b")
                                .build(),
                        GradeConfig.GradeBand.builder()
                                .label("C")
                                .minScore(BigDecimal.ZERO)
                                .maxScore(BigDecimal.valueOf(62.99))
                                .color("#64748b")
                                .build()
                )))
                .targetDistribution(Map.of(
                        "S", BigDecimal.valueOf(0.10),
                        "A", BigDecimal.valueOf(0.20),
                        "B", BigDecimal.valueOf(0.50),
                        "C", BigDecimal.valueOf(0.20)
                ))
                .build();
    }

    private GroupResDto toGroupResDto(EvaluationGroup group) {
        List<EvaluatorMapping> maps = group.getEvaluatorMaps() != null
                ? group.getEvaluatorMaps()
                : List.of();

        Set<UUID> memberIds = new LinkedHashSet<>();
        for (EvaluatorMapping map : maps) {
            if (map == null) continue;
            if (map.getTargetMemberId() != null) memberIds.add(map.getTargetMemberId());
            if (map.getEvaluatorId() != null) memberIds.add(map.getEvaluatorId());
        }

        Map<UUID, MemberMinimalProfileDto> fetchedProfiles = Map.of();
        if (!memberIds.isEmpty()) {
            try {
                fetchedProfiles = memberServiceClient.getProfilesByIds(List.copyOf(memberIds));
            } catch (Exception ignored) {
                fetchedProfiles = Map.of();
            }
        }
        final Map<UUID, MemberMinimalProfileDto> profiles = fetchedProfiles;

        List<EvaluatorMappingResDto> resMaps = maps.stream()
                .map(map -> {
                    MemberMinimalProfileDto targetProfile = profiles.get(map.getTargetMemberId());
                    MemberMinimalProfileDto evaluatorProfile = profiles.get(map.getEvaluatorId());
                    return EvaluatorMappingResDto.builder()
                            .targetMemberId(map.getTargetMemberId())
                            .evaluatorId(map.getEvaluatorId())
                            .evaluationType(map.getEvaluationType())
                            .targetMemberProfileUrl(targetProfile != null ? targetProfile.getProfileUrl() : null)
                            .evaluatorProfileUrl(evaluatorProfile != null ? evaluatorProfile.getProfileUrl() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return GroupResDto.from(group, resMaps);
    }

    private GroupResDto filterGroupForScope(GroupResDto group, EvaluationAccessScopeService.AccessScope scope) {
        List<EvaluatorMappingResDto> visibleMaps = group.getEvaluatorMaps() == null
                ? List.of()
                : group.getEvaluatorMaps().stream()
                .filter(map -> scope.canSeeTarget(map.getTargetMemberId()) || scope.isRequester(map.getEvaluatorId()))
                .collect(Collectors.toList());

        Set<UUID> visibleTargets = new LinkedHashSet<>();
        List<UUID> originalTargets = readTargetMemberIds(group.getTargetMemberIdsJson());
        originalTargets.stream()
                .filter(scope::canSeeTarget)
                .forEach(visibleTargets::add);
        visibleMaps.stream()
                .map(EvaluatorMappingResDto::getTargetMemberId)
                .filter(java.util.Objects::nonNull)
                .forEach(visibleTargets::add);

        String targetMemberIdsJson;
        try {
            targetMemberIdsJson = objectMapper.writeValueAsString(visibleTargets);
        } catch (Exception e) {
            targetMemberIdsJson = "[]";
        }

        return GroupResDto.builder()
                .groupId(group.getGroupId())
                .companyId(group.getCompanyId())
                .seasonId(group.getSeasonId())
                .name(group.getName())
                .evaluationTypesJson(group.getEvaluationTypesJson())
                .targetMemberIdsJson(targetMemberIdsJson)
                .designId(group.getDesignId())
                .evaluatorMaps(visibleMaps)
                .build();
    }

    private boolean hasVisibleTargets(GroupResDto group) {
        return !readTargetMemberIds(group.getTargetMemberIdsJson()).isEmpty();
    }

    private boolean hasVisibleMappings(GroupResDto group) {
        return group.getEvaluatorMaps() != null && !group.getEvaluatorMaps().isEmpty();
    }

    private List<UUID> readTargetMemberIds(String targetMemberIdsJson) {
        if (targetMemberIdsJson == null || targetMemberIdsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    targetMemberIdsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<UUID>>() {}
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 그룹 생성 시점 초기 매핑 생성.
     * SELF 유형이 포함되어 있으면 각 대상자에 대해 (target -> target, SELF) 를 즉시 저장한다.
     */
    private List<EvaluatorMapping> buildInitialEvaluatorMappings(List<String> evaluationTypes, List<UUID> targetMemberIds) {
        if (evaluationTypes == null || targetMemberIds == null) return List.of();
        boolean hasSelf = evaluationTypes.stream()
                .filter(Objects::nonNull)
                .map(v -> v.trim().toUpperCase())
                .anyMatch("SELF"::equals);
        if (!hasSelf) return List.of();
        return targetMemberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(targetId -> EvaluatorMapping.builder()
                        .targetMemberId(targetId)
                        .evaluatorId(targetId)
                        .evaluationType(EvalType.SELF)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * evaluatorMaps 를 단일 소스 오브 트루스로 보고
     * 그룹의 evaluationTypesJson / targetMemberIdsJson 을 동기화한다.
     */
    private void syncGroupScopeFieldsFromMaps(EvaluationGroup group, List<EvaluatorMapping> maps) {
        Set<String> evalTypes = new LinkedHashSet<>();
        Set<UUID> targetIds = new LinkedHashSet<>();
        if (maps != null) {
            for (EvaluatorMapping map : maps) {
                if (map == null) continue;
                if (map.getEvaluationType() != null) {
                    evalTypes.add(map.getEvaluationType().name());
                }
                if (map.getTargetMemberId() != null) {
                    targetIds.add(map.getTargetMemberId());
                }
            }
        }
        String evalTypesJson;
        String targetMemberIdsJson;
        try {
            evalTypesJson = objectMapper.writeValueAsString(evalTypes);
            targetMemberIdsJson = objectMapper.writeValueAsString(targetIds);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "그룹 매핑 동기화에 실패했습니다.");
        }
        group.update(
                group.getName(),
                evalTypesJson,
                targetMemberIdsJson,
                group.getEvaluatorMaps()
        );
    }
}
