package com._team._team.evaluation.dto.reqdto;

import com._team._team.goal.domain.enums.Grade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 평가자 calibration upsert.
 *
 *   role=ASSISTANT → suggestedGrades 만 사용
 *   role=LEAD      → finalGrades 도 입력 가능
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationUpsertReqDto {

    @Builder.Default
    private Map<UUID, Grade> suggestedGrades = new HashMap<>();

    @Builder.Default
    private Map<UUID, Grade> finalGrades = new HashMap<>();

    private String comment;

    /** true 이면 submittedAt 기록 */
    @Builder.Default
    private boolean submit = false;
}
