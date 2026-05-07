package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.evaluation.domain.converter.EvaluatorMapping;
import com._team._team.evaluation.domain.converter.EvaluatorMappingListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evaluation_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private EvaluationSeason season;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "evaluation_types_json", columnDefinition = "JSON")
    private String evaluationTypesJson;

    @Column(name = "target_member_ids_json", columnDefinition = "JSON")
    private String targetMemberIdsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private EvaluationDesign design;

    /**
     * 타입 세이프 List<EvaluatorMapping>.
     * DB 컬럼 이름/타입(JSON) 은 동일하게 유지. 직렬화는 EvaluatorMappingListConverter 가 담당.
     */
    @Convert(converter = EvaluatorMappingListConverter.class)
    @Column(name = "evaluator_maps_json", columnDefinition = "JSON")
    @Builder.Default
    private List<EvaluatorMapping> evaluatorMaps = new ArrayList<>();

    public void update(String name, String evaluationTypesJson, String targetMemberIdsJson,
                       List<EvaluatorMapping> evaluatorMaps) {
        this.name = name;
        this.evaluationTypesJson = evaluationTypesJson;
        this.targetMemberIdsJson = targetMemberIdsJson;
        if (evaluatorMaps != null) {
            this.evaluatorMaps = new ArrayList<>(evaluatorMaps);
        }
    }

    public void assignDesign(EvaluationDesign design) {
        this.design = design;
    }

    public void updateEvaluatorMaps(List<EvaluatorMapping> evaluatorMaps) {
        this.evaluatorMaps = evaluatorMaps != null ? new ArrayList<>(evaluatorMaps) : new ArrayList<>();
    }
}
