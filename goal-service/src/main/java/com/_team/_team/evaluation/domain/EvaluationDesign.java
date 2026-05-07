package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.converter.EvaluationSectionListConverter;
import com._team._team.evaluation.domain.converter.GradeConfig;
import com._team._team.evaluation.domain.converter.GradeConfigConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evaluation_design")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationDesign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "design_id")
    private UUID designId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * [D-4] 섹션 목록 — sections_json 컬럼을 JPA Converter 로 타입 세이프하게 매핑.
     * 기존 raw JSON 컬럼은 그대로 유지한 채 애플리케이션에서는 VO 리스트로 사용.
     */
    @Convert(converter = EvaluationSectionListConverter.class)
    @Column(name = "sections_json", columnDefinition = "JSON")
    @Builder.Default
    private List<EvaluationSection> sections = new ArrayList<>();

    /**
     * [D-4] 등급 설정 — grade_config_json 컬럼을 JPA Converter 로 매핑.
     */
    @Convert(converter = GradeConfigConverter.class)
    @Column(name = "grade_config_json", columnDefinition = "JSON")
    private GradeConfig gradeConfig;

    /** 설계 버전 — 수정 시 증가 */
    @Column(name = "template_version")
    private Integer templateVersion;

    /** 기본 템플릿 여부 */
    @Column(name = "is_default_template")
    private Boolean defaultTemplate;

    /**
     * [D-4] 타입 세이프 update. null 인자는 "변경 없음"이 아니라
     * 호출부에서 병합한 뒤 넘겨야 함 (EvaluationDesignService 에서 병합 책임).
     */
    public void update(String name, List<EvaluationSection> sections, GradeConfig gradeConfig) {
        if (name != null) {
            this.name = name;
        }
        if (sections != null) {
            this.sections = sections;
        }
        if (gradeConfig != null) {
            this.gradeConfig = gradeConfig;
        }
        this.templateVersion = (this.templateVersion == null ? 1 : this.templateVersion) + 1;
    }

    public void markDefaultTemplate(boolean value) {
        this.defaultTemplate = value;
    }
}
