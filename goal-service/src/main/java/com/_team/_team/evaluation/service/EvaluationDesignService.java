package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationDesign;
import com._team._team.evaluation.domain.converter.DesignQuestion;
import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.converter.GradeConfig;
import com._team._team.evaluation.domain.enums.SectionType;
import com._team._team.evaluation.dto.reqdto.DesignCreateReqDto;
import com._team._team.evaluation.dto.reqdto.DesignUpdateReqDto;
import com._team._team.evaluation.dto.resdto.DesignResDto;
import com._team._team.evaluation.repository.EvaluationDesignRepository;
import com._team._team.evaluation.repository.EvaluationGroupRepository;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationDesignService {

    private final EvaluationDesignRepository designRepository;
    private final EvaluationGroupRepository groupRepository;
    private final EvaluationResponseRepository responseRepository;

    public EvaluationDesignService(
            EvaluationDesignRepository designRepository,
            EvaluationGroupRepository groupRepository,
            EvaluationResponseRepository responseRepository) {
        this.designRepository = designRepository;
        this.groupRepository = groupRepository;
        this.responseRepository = responseRepository;
    }

    @Transactional(readOnly = true)
    public List<DesignResDto> listDesigns(UUID companyId) {
        return designRepository.findByCompanyId(companyId)
                .stream().map(DesignResDto::from).collect(Collectors.toList());
    }

    @Transactional
    public DesignResDto createDesign(UUID companyId, DesignCreateReqDto dto) {
        String name = normalizeAndValidateName(dto.getName());
        List<EvaluationSection> sections = validateAndNormalizeSections(dto.getSections());
        GradeConfig gradeConfig = validateAndNormalizeGradeConfig(dto.getGradeConfig());

        EvaluationDesign design = EvaluationDesign.builder()
                .companyId(companyId)
                .name(name)
                .sections(sections)
                .gradeConfig(gradeConfig)
                .templateVersion(1)
                .defaultTemplate(!designRepository.existsByCompanyIdAndDefaultTemplateTrue(companyId))
                .build();
        return DesignResDto.from(designRepository.save(design));
    }

    /**
     * 평가 작성 화면 등에서 설계 본문을 조회한다.
     * {@code EVALUATION:READ} 권한 없이 접근하는 평가자는, 해당 설계를 사용하는 그룹에 본인이 배정된 경우에만 허용한다.
     */
    @Transactional(readOnly = true)
    public DesignResDto getDesign(UUID designId, UUID companyId, UUID requesterMemberId) {
        EvaluationDesign design = findDesignOrThrow(designId, companyId);
        if (!responseRepository.existsEvaluatorAssignmentForDesign(requesterMemberId, companyId, designId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "이 평가 설계에 접근할 수 없습니다.");
        }
        return DesignResDto.from(design);
    }

    @Transactional
    public DesignResDto updateDesign(UUID designId, UUID companyId, DesignUpdateReqDto dto) {
        EvaluationDesign design = findDesignOrThrow(designId, companyId);

        String name = null;
        if (dto.getName() != null) {
            name = normalizeAndValidateName(dto.getName());
        }

        List<EvaluationSection> sections = null;
        if (dto.getSections() != null) {
            sections = validateAndNormalizeSections(dto.getSections());
        }

        GradeConfig gradeConfig = null;
        if (dto.getGradeConfig() != null) {
            gradeConfig = validateAndNormalizeGradeConfig(dto.getGradeConfig());
        }

        design.update(name, sections, gradeConfig);
        return DesignResDto.from(design);
    }

    @Transactional
    public DesignResDto duplicateDesign(UUID designId, UUID companyId) {
        EvaluationDesign source = findDesignOrThrow(designId, companyId);
        EvaluationDesign duplicated = EvaluationDesign.builder()
                .companyId(companyId)
                .name(source.getName() + " (복제)")
                .sections(source.getSections() != null ? new ArrayList<>(source.getSections()) : new ArrayList<>())
                .gradeConfig(source.getGradeConfig())
                .templateVersion(1)
                .defaultTemplate(false)
                .build();
        return DesignResDto.from(designRepository.save(duplicated));
    }

    @Transactional
    public void deleteDesign(UUID designId, UUID companyId) {
        EvaluationDesign design = findDesignOrThrow(designId, companyId);
        long usedByGroups = groupRepository.countByDesign_DesignId(designId);
        if (usedByGroups > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "현재 " + usedByGroups + "개 그룹에서 사용 중인 설계는 삭제할 수 없습니다.");
        }
        if (Boolean.TRUE.equals(design.getDefaultTemplate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "기본 템플릿은 삭제할 수 없습니다.");
        }
        designRepository.delete(design);
    }

    private EvaluationDesign findDesignOrThrow(UUID designId, UUID companyId) {
        EvaluationDesign design = designRepository.findById(designId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 설계를 찾을 수 없습니다."));
        if (!design.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "평가 설계를 찾을 수 없습니다.");
        }
        return design;
    }

    /**
     * KPI 자동 섹션 제거 정책:
     * - KPI_SCORE 타입은 허용하지 않는다.
     * - 잔존 kpiFilter 값은 무시한다.
     */
    private List<EvaluationSection> validateAndNormalizeSections(List<EvaluationSection> sections) {
        List<EvaluationSection> safe = sections != null ? sections : new ArrayList<>();
        if (safe.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "평가 설계에는 최소 1개 이상의 섹션이 필요합니다.");
        }
        List<EvaluationSection> list = new ArrayList<>();
        for (EvaluationSection section : safe) {
            if (section == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "섹션 정보가 올바르지 않습니다.");
            }
            list.add(section);
        }
        for (EvaluationSection section : list) {
            if (section.resolveType() == SectionType.KPI_SCORE) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "KPI 자동 섹션은 더 이상 지원하지 않습니다.");
            }
            if (section.getTitle() == null || section.getTitle().trim().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "섹션 제목은 비어 있을 수 없습니다.");
            }
            if (section.getWeight() == null || section.getWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "섹션 가중치는 0 이상이어야 합니다.");
            }
            validateAndNormalizeQuestionsInSection(section);
            section.setKpiFilter(null);
        }
        validateSectionWeightTotal(list);
        return list;
    }

    /**
     * 섹션 가중치 합계 100% — {@code SectionScorer} 총점 가중 평균과 동일한 계약.
     */
    private void validateSectionWeightTotal(List<EvaluationSection> sections) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EvaluationSection s : sections) {
            sum = sum.add(s.getWeight() != null ? s.getWeight() : BigDecimal.ZERO);
        }
        if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(new BigDecimal("100.00")) != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "섹션 가중치 합계는 정확히 100%여야 합니다. (현재 " + sum.stripTrailingZeros().toPlainString() + "%)");
        }
    }

    private boolean isScoredQuestionType(String type) {
        if (type == null) return false;
        String t = type.trim().toLowerCase(Locale.ROOT);
        return "scale".equals(t) || "grade".equals(t) || "gap".equals(t);
    }

    private void validateAndNormalizeQuestionsInSection(EvaluationSection section) {
        List<DesignQuestion> questions = section.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "각 섹션에는 최소 1개 이상의 문항이 필요합니다.");
        }
        Set<String> allowedQuestionTypes = Set.of("text", "scale", "grade", "gap");
        for (DesignQuestion question : questions) {
            if (question == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "문항 정보가 올바르지 않습니다.");
            }
            if (question.getTitle() == null || question.getTitle().trim().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "문항 제목은 비어 있을 수 없습니다.");
            }
            String type = question.getType() == null ? "text" : question.getType().trim().toLowerCase(Locale.ROOT);
            if (!allowedQuestionTypes.contains(type)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "지원하지 않는 문항 타입입니다: " + question.getType());
            }
            question.setType(type);
            if (question.getRequired() == null) {
                question.setRequired(Boolean.TRUE);
            }
        }

        List<DesignQuestion> scored = new ArrayList<>();
        for (DesignQuestion q : questions) {
            if (q != null && isScoredQuestionType(q.getType())) {
                scored.add(q);
            }
        }
        if (scored.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "섹션 \"" + section.getTitle().trim() + "\"에는 채점 문항(scale/grade/gap)이 최소 1개 필요합니다.");
        }

        boolean allScoredWeightsMissing = scored.stream().allMatch(q ->
                q.getWeight() == null || q.getWeight().compareTo(BigDecimal.ZERO) <= 0);
        if (allScoredWeightsMissing) {
            distributeEqualQuestionWeights(scored);
        } else {
            BigDecimal sumW = BigDecimal.ZERO;
            for (DesignQuestion q : scored) {
                if (q.getWeight() == null || q.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "섹션 \"" + section.getTitle().trim()
                                    + "\"의 채점 문항 가중치는 모두 양수이거나, 모두 비워 균등 배분되어야 합니다.");
                }
                sumW = sumW.add(q.getWeight());
            }
            if (sumW.setScale(2, RoundingMode.HALF_UP).compareTo(new BigDecimal("100.00")) != 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "섹션 \"" + section.getTitle().trim() + "\"의 채점 문항 가중치 합계는 100%여야 합니다. (현재 "
                                + sumW.stripTrailingZeros().toPlainString() + "%)");
            }
        }

        for (DesignQuestion q : questions) {
            if (q != null && !isScoredQuestionType(q.getType())) {
                q.setWeight(BigDecimal.ZERO);
            }
        }
    }

    private void distributeEqualQuestionWeights(List<DesignQuestion> scored) {
        int n = scored.size();
        int base = 100 / n;
        int rem = 100 - base * n;
        for (int i = 0; i < n; i++) {
            int w = base + (i < rem ? 1 : 0);
            scored.get(i).setWeight(BigDecimal.valueOf(w));
        }
    }

    private GradeConfig validateAndNormalizeGradeConfig(GradeConfig gradeConfig) {
        if (gradeConfig == null) return null;

        String type = gradeConfig.getType() == null ? "ABSOLUTE" : gradeConfig.getType().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("ABSOLUTE") && !type.equals("RELATIVE")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 설정 type은 ABSOLUTE 또는 RELATIVE만 허용됩니다.");
        }
        gradeConfig.setType(type);

        List<GradeConfig.GradeBand> grades = gradeConfig.getGrades();
        if (grades == null || grades.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 구간(grades)은 최소 1개 이상이어야 합니다.");
        }
        for (GradeConfig.GradeBand grade : grades) {
            if (grade == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 구간 정보가 올바르지 않습니다.");
            }
            if (grade.getLabel() == null || grade.getLabel().trim().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 구간 label은 비어 있을 수 없습니다.");
            }
            if (grade.getMinScore() == null || grade.getMaxScore() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 구간의 최소/최대 점수는 필수입니다.");
            }
            if (grade.getMinScore().compareTo(grade.getMaxScore()) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "등급 구간의 최소 점수는 최대 점수보다 클 수 없습니다.");
            }
        }

        Map<String, BigDecimal> targetDistribution = gradeConfig.getTargetDistribution();
        if ("RELATIVE".equals(type)) {
            if (targetDistribution == null || targetDistribution.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "상대평가(RELATIVE)는 targetDistribution이 필요합니다.");
            }
            BigDecimal distSum = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : targetDistribution.entrySet()) {
                if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "targetDistribution 등급 키가 올바르지 않습니다.");
                }
                BigDecimal value = entry.getValue();
                if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "targetDistribution 값은 0~1 범위여야 합니다.");
                }
                distSum = distSum.add(value);
            }
            if (distSum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.02")) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "상대평가 목표 분포 비율 합계는 1.0(100%)이어야 합니다. (현재 " + distSum.setScale(4, RoundingMode.HALF_UP) + ")");
            }
        }

        return gradeConfig;
    }

    private String normalizeAndValidateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "평가 설계 이름은 필수입니다.");
        }
        if (name.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "평가 설계 이름은 100자를 초과할 수 없습니다.");
        }
        return name;
    }

}
