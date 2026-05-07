package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationGroup;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupResDto {
    private UUID groupId;
    private UUID companyId;
    private UUID seasonId;
    private String name;
    private String evaluationTypesJson;
    private String targetMemberIdsJson;
    private UUID designId;

    /** [D-3] evaluatorMaps 구조화 리스트 */
    private List<EvaluatorMappingResDto> evaluatorMaps;

    public static GroupResDto from(EvaluationGroup e, List<EvaluatorMappingResDto> maps) {
        return GroupResDto.builder()
                .groupId(e.getGroupId())
                .companyId(e.getCompanyId())
                .seasonId(e.getSeason().getSeasonId())
                .name(e.getName())
                .evaluationTypesJson(e.getEvaluationTypesJson())
                .targetMemberIdsJson(e.getTargetMemberIdsJson())
                .designId(e.getDesign() != null ? e.getDesign().getDesignId() : null)
                .evaluatorMaps(maps != null ? maps : List.of())
                .build();
    }
}
