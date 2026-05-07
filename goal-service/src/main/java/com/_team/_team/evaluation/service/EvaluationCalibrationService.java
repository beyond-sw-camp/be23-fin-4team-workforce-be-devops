package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.EvaluationDesign;
import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.converter.GradeConfig;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.dto.reqdto.CalibrationUpsertReqDto;
import com._team._team.evaluation.dto.reqdto.ConfirmReqDto;
import com._team._team.evaluation.dto.resdto.CalibrationResDto;
import com._team._team.evaluation.dto.resdto.EvaluationResponseResDto;
import com._team._team.evaluation.repository.EvaluationCalibrationRepository;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.util.GoalSnapshot;
import com._team._team.evaluation.util.GradeScoreCalculator;
import com._team._team.goal.domain.enums.Grade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationCalibrationService {

    private final EvaluationCalibrationRepository calibrationRepository;
    private final EvaluationResponseRepository responseRepository;
    private final ObjectMapper objectMapper;

    public EvaluationCalibrationService(EvaluationCalibrationRepository calibrationRepository,
                                        EvaluationResponseRepository responseRepository,
                                        ObjectMapper objectMapper) {
        this.calibrationRepository = calibrationRepository;
        this.responseRepository = responseRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CalibrationResDto> list(UUID responseId) {
        return calibrationRepository.findByResponseId(responseId)
                .stream()
                .map(CalibrationResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CalibrationResDto upsert(UUID responseId, UUID evaluatorId, CalibrationUpsertReqDto dto) {
        EvaluationResponse response = mustGetResponse(responseId);
        ensureCalibrationStage(response);
        ensureLeadCalibrationExists(response);

        EvaluationCalibration calibration = calibrationRepository
                .findByResponseIdAndEvaluatorId(responseId, evaluatorId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "You do not have calibration permission for this response."
                ));

        if (response.getStage() == EvaluationStage.SELF_SUBMITTED
                || response.getStage() == EvaluationStage.PEER_OPEN
                || response.getStage() == EvaluationStage.UPWARD_OPEN
                || response.getStage() == EvaluationStage.DOWNWARD_OPEN) {
            response.openCalibration();
        }

        calibration.saveSuggested(serializeMap(dto.getSuggestedGrades()), dto.getComment());

        if (calibration.getRole() == CalibrationRole.LEAD
                && dto.getFinalGrades() != null
                && !dto.getFinalGrades().isEmpty()) {
            calibration.saveFinal(serializeMap(dto.getFinalGrades()), dto.getComment());
        }

        if (dto.isSubmit()) {
            calibration.submit();
        }

        return CalibrationResDto.from(calibration);
    }

    @Transactional
    public EvaluationResponseResDto confirm(UUID responseId, UUID actorId, ConfirmReqDto dto) {
        EvaluationResponse response = mustGetResponse(responseId);
        ensureLeadCalibrationExists(response);

        EvaluationCalibration lead = calibrationRepository
                .findByResponseIdAndRole(responseId, CalibrationRole.LEAD)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Lead calibration entry was not found."
                ));
        if (!lead.getEvaluatorId().equals(actorId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the lead evaluator can confirm this response.");
        }
        if (isBlank(lead.getFinalGradeJson())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Every KR must have a final grade before confirmation."
            );
        }

        Map<UUID, Grade> finalGrades = deserializeMap(lead.getFinalGradeJson());
        GoalSnapshot snapshot = deserializeSnapshot(response.getGoalSnapshotJson());
        validateFinalGrades(snapshot, finalGrades);

        BigDecimal finalScore = GradeScoreCalculator.finalScore(snapshot, finalGrades, null);
        EvaluationDesign design = response.getGroup() != null ? response.getGroup().getDesign() : null;
        GradeConfig gradeConfig = design != null ? design.getGradeConfig() : null;

        if (isRelativeGradeConfig(gradeConfig)) {
            applyRelativeConfirmation(response, actorId, finalScore, gradeConfig);
            return EvaluationResponseResDto.from(response);
        }

        String resolvedGrade = resolveAbsoluteConfirmedGrade(finalScore, gradeConfig, dto);
        response.confirm(actorId, resolvedGrade, finalScore, finalScore);
        return EvaluationResponseResDto.from(response);
    }

    @Transactional
    public EvaluationResponseResDto unconfirm(UUID responseId, UUID actorId) {
        EvaluationResponse response = mustGetResponse(responseId);
        ensureLeadCalibrationExists(response);
        EvaluationCalibration lead = calibrationRepository
                .findByResponseIdAndRole(responseId, CalibrationRole.LEAD)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Lead calibration entry was not found."
                ));
        if (!lead.getEvaluatorId().equals(actorId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the lead evaluator can unconfirm this response.");
        }
        response.unconfirm();
        return EvaluationResponseResDto.from(response);
    }

    private EvaluationResponse mustGetResponse(UUID responseId) {
        return responseRepository.findById(responseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Evaluation response was not found."));
    }

    private void ensureCalibrationStage(EvaluationResponse response) {
        EvaluationStage stage = response.getStage();
        if (stage == EvaluationStage.SELF_PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Self evaluation must be submitted first.");
        }
        if (stage == EvaluationStage.CONFIRMED || stage == EvaluationStage.SKIPPED_LEAVER) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "This response is already finalized.");
        }
    }

    private void ensureLeadCalibrationExists(EvaluationResponse response) {
        if (response.getEvaluatorId() == null) {
            return;
        }
        if (calibrationRepository.findByResponseIdAndRole(response.getResponseId(), CalibrationRole.LEAD).isPresent()) {
            return;
        }
        calibrationRepository.save(EvaluationCalibration.builder()
                .responseId(response.getResponseId())
                .evaluatorId(response.getEvaluatorId())
                .role(CalibrationRole.LEAD)
                .build());
    }

    private String serializeMap(Map<UUID, Grade> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            Map<String, Grade> stringKeyed = new HashMap<>();
            map.forEach((key, value) -> stringKeyed.put(key.toString(), value));
            return objectMapper.writeValueAsString(stringKeyed);
        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize calibration grades.");
        }
    }

    private Map<UUID, Grade> deserializeMap(String json) {
        if (isBlank(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, Grade> stringKeyed = objectMapper.readValue(json, new TypeReference<Map<String, Grade>>() {});
            Map<UUID, Grade> uuidKeyed = new HashMap<>();
            stringKeyed.forEach((key, value) -> uuidKeyed.put(UUID.fromString(key), value));
            return uuidKeyed;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize calibration grades.");
        }
    }

    private GoalSnapshot deserializeSnapshot(String json) {
        if (isBlank(json)) {
            return GoalSnapshot.builder().goals(List.of()).build();
        }
        try {
            return objectMapper.readValue(json, GoalSnapshot.class);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize goal snapshot.");
        }
    }

    private void validateFinalGrades(GoalSnapshot snapshot, Map<UUID, Grade> finalGrades) {
        if (snapshot == null || snapshot.getGoals() == null || snapshot.getGoals().isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "No KR snapshot exists for this response.");
        }
        if (finalGrades == null || finalGrades.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Final KR grades are empty.");
        }

        List<UUID> snapshotGoalIds = snapshot.getGoals().stream()
                .map(GoalSnapshot.GoalEntry::getGoalId)
                .filter(Objects::nonNull)
                .toList();
        List<UUID> missing = snapshotGoalIds.stream()
                .filter(goalId -> finalGrades.get(goalId) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Every KR in the snapshot needs a final grade. missingGoalIds=" + missing
            );
        }

        List<UUID> extras = finalGrades.keySet().stream()
                .filter(goalId -> !snapshotGoalIds.contains(goalId))
                .toList();
        if (!extras.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Final grades contain KR ids outside the snapshot. extraGoalIds=" + extras
            );
        }
    }

    private boolean isRelativeGradeConfig(GradeConfig gradeConfig) {
        return gradeConfig != null
                && gradeConfig.getType() != null
                && "RELATIVE".equalsIgnoreCase(gradeConfig.getType())
                && gradeConfig.getTargetDistribution() != null
                && !gradeConfig.getTargetDistribution().isEmpty();
    }

    private String resolveAbsoluteConfirmedGrade(BigDecimal finalScore, GradeConfig gradeConfig, ConfirmReqDto dto) {
        if (gradeConfig != null && gradeConfig.getGrades() != null) {
            for (GradeConfig.GradeBand band : gradeConfig.getGrades()) {
                if (band == null || isBlank(band.getLabel()) || band.getMinScore() == null || band.getMaxScore() == null) {
                    continue;
                }
                if (finalScore.compareTo(band.getMinScore()) >= 0 && finalScore.compareTo(band.getMaxScore()) <= 0) {
                    return band.getLabel().trim().toUpperCase();
                }
            }
        }

        if (dto != null && dto.getConfirmedGrade() != null) {
            return dto.getConfirmedGrade().name();
        }
        if (finalScore.compareTo(new BigDecimal("92.50")) >= 0) {
            return Grade.S.name();
        }
        if (finalScore.compareTo(new BigDecimal("77.50")) >= 0) {
            return Grade.A.name();
        }
        if (finalScore.compareTo(new BigDecimal("62.50")) >= 0) {
            return Grade.B.name();
        }
        return Grade.C.name();
    }

    private void applyRelativeConfirmation(EvaluationResponse currentResponse,
                                           UUID actorId,
                                           BigDecimal currentScore,
                                           GradeConfig gradeConfig) {
        List<EvaluationResponse> cohort = responseRepository.findByGroup_GroupIdOrderByCreatedAtAsc(
                        currentResponse.getGroup().getGroupId())
                .stream()
                .filter(response -> response.getStage() == EvaluationStage.CONFIRMED
                        || response.getResponseId().equals(currentResponse.getResponseId()))
                .filter(response -> response.getStage() != EvaluationStage.SKIPPED_LEAVER)
                .toList();

        Map<UUID, BigDecimal> scoreByResponseId = new LinkedHashMap<>();
        for (EvaluationResponse response : cohort) {
            if (response.getResponseId().equals(currentResponse.getResponseId())) {
                scoreByResponseId.put(response.getResponseId(), currentScore);
                continue;
            }
            if (response.getFinalScoreSnapshot() != null) {
                scoreByResponseId.put(response.getResponseId(), response.getFinalScoreSnapshot());
            }
        }

        Map<UUID, String> assignedGrades = assignRelativeGrades(cohort, scoreByResponseId, gradeConfig);
        String currentGrade = assignedGrades.get(currentResponse.getResponseId());
        if (isBlank(currentGrade)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to assign a relative grade.");
        }

        if (currentResponse.getStage() == EvaluationStage.CONFIRMED) {
            currentResponse.refreshConfirmedOutcome(currentGrade, currentScore, currentScore);
        } else {
            currentResponse.confirm(actorId, currentGrade, currentScore, currentScore);
        }

        for (EvaluationResponse response : cohort) {
            if (response.getResponseId().equals(currentResponse.getResponseId())) {
                continue;
            }
            String reassignedGrade = assignedGrades.get(response.getResponseId());
            BigDecimal score = scoreByResponseId.get(response.getResponseId());
            if (response.getStage() == EvaluationStage.CONFIRMED
                    && !isBlank(reassignedGrade)
                    && score != null) {
                response.refreshConfirmedOutcome(reassignedGrade, score, score);
            }
        }
    }

    private Map<UUID, String> assignRelativeGrades(List<EvaluationResponse> cohort,
                                                   Map<UUID, BigDecimal> scoreByResponseId,
                                                   GradeConfig gradeConfig) {
        List<String> orderedLabels = gradeConfig.getGrades() != null && !gradeConfig.getGrades().isEmpty()
                ? gradeConfig.getGrades().stream()
                    .map(GradeConfig.GradeBand::getLabel)
                    .filter(label -> !isBlank(label))
                    .map(label -> label.trim().toUpperCase())
                    .toList()
                : List.of(Grade.S.name(), Grade.A.name(), Grade.B.name(), Grade.C.name());

        List<ScoredCohortEntry> ranked = cohort.stream()
                .map(response -> new ScoredCohortEntry(
                        response,
                        scoreByResponseId.getOrDefault(response.getResponseId(), BigDecimal.ZERO)))
                .sorted(Comparator
                        .comparing(ScoredCohortEntry::score).reversed()
                        .thenComparing(entry -> entry.response().getCreatedAt(), Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(entry -> entry.response().getResponseId()))
                .toList();

        Map<String, Integer> quotas = computeRelativeQuotas(
                ranked.size(),
                orderedLabels,
                gradeConfig.getTargetDistribution()
        );

        Map<UUID, String> assigned = new HashMap<>();
        int cursor = 0;
        for (String label : orderedLabels) {
            int quota = quotas.getOrDefault(label, 0);
            for (int i = 0; i < quota && cursor < ranked.size(); i++) {
                assigned.put(ranked.get(cursor).response().getResponseId(), label);
                cursor++;
            }
        }

        String fallback = orderedLabels.isEmpty() ? Grade.C.name() : orderedLabels.get(orderedLabels.size() - 1);
        while (cursor < ranked.size()) {
            assigned.put(ranked.get(cursor).response().getResponseId(), fallback);
            cursor++;
        }
        return assigned;
    }

    private Map<String, Integer> computeRelativeQuotas(int totalCount,
                                                       List<String> orderedLabels,
                                                       Map<String, BigDecimal> targetDistribution) {
        Map<String, Integer> quotas = new LinkedHashMap<>();
        if (totalCount <= 0 || orderedLabels.isEmpty()) {
            return quotas;
        }

        BigDecimal totalRatio = orderedLabels.stream()
                .map(label -> targetDistribution.getOrDefault(label, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRatio.compareTo(BigDecimal.ZERO) <= 0) {
            int even = totalCount / orderedLabels.size();
            int remainder = totalCount % orderedLabels.size();
            for (int i = 0; i < orderedLabels.size(); i++) {
                quotas.put(orderedLabels.get(i), even + (i < remainder ? 1 : 0));
            }
            return quotas;
        }

        Map<String, BigDecimal> fractionalRemainders = new HashMap<>();
        int assignedBase = 0;
        for (String label : orderedLabels) {
            BigDecimal ratio = targetDistribution.getOrDefault(label, BigDecimal.ZERO);
            BigDecimal exact = ratio.multiply(BigDecimal.valueOf(totalCount))
                    .divide(totalRatio, 8, RoundingMode.HALF_UP);
            int base = exact.setScale(0, RoundingMode.DOWN).intValue();
            quotas.put(label, base);
            fractionalRemainders.put(label, exact.subtract(BigDecimal.valueOf(base)));
            assignedBase += base;
        }

        int remaining = totalCount - assignedBase;
        List<String> remainderOrder = orderedLabels.stream()
                .sorted(Comparator
                        .comparing((String label) -> fractionalRemainders.getOrDefault(label, BigDecimal.ZERO))
                        .reversed()
                        .thenComparing(orderedLabels::indexOf))
                .toList();

        for (int i = 0; i < remaining; i++) {
            String label = remainderOrder.get(i % remainderOrder.size());
            quotas.put(label, quotas.getOrDefault(label, 0) + 1);
        }
        return quotas;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ScoredCohortEntry(EvaluationResponse response, BigDecimal score) {
    }
}
