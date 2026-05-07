package com._team._team.evaluation.util;

import com._team._team.evaluation.domain.enums.EvaluationStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EvaluationSchedule — 시즌의 단계 일정 정의 (scheduleJson 직렬화 포맷).
 *
 *  phases : 평가 단계 순서 (옵션 단계 PEER/UPWARD/DOWNWARD 포함 여부)
 *  각 phase 의 start/end 일자
 *
 *  예시 (3단계 — 응답 stage 명과 일치):
 *    {
 *      "phases": [
 *        { "stage": "SELF_SUBMITTED",     "start": "2026-04-15", "end": "2026-04-20" },
 *        { "stage": "CALIBRATION_OPEN",   "start": "2026-04-21", "end": "2026-04-25" },
 *        { "stage": "CONFIRMED",          "start": "2026-04-26", "end": "2026-04-30" }
 *      ]
 *    }
 *
 *  옵션 단계 추가 시 (5단계 예):
 *    SELF_SUBMITTED → PEER_OPEN → UPWARD_OPEN → CALIBRATION_OPEN → CONFIRMED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationSchedule {

    @Builder.Default
    private List<Phase> phases = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Phase {
        private EvaluationStage stage;
        private LocalDate start;
        private LocalDate end;
    }

    public boolean hasPhase(EvaluationStage stage) {
        return phases.stream().anyMatch(p -> p.getStage() == stage);
    }

    public Phase findPhase(EvaluationStage stage) {
        return phases.stream().filter(p -> p.getStage() == stage).findFirst().orElse(null);
    }

    /** 다음 단계 결정 — 현재 stage 다음에 정의된 phase 반환 */
    public EvaluationStage nextStage(EvaluationStage current) {
        for (int i = 0; i < phases.size() - 1; i++) {
            if (phases.get(i).getStage() == current) {
                return phases.get(i + 1).getStage();
            }
        }
        return current; // 마지막 단계면 그대로
    }

    /** scheduleJson 파싱 helper */
    public static EvaluationSchedule parse(String json, ObjectMapper om) {
        if (json == null || json.isBlank()) return defaultSchedule();
        try {
            return om.readValue(json, EvaluationSchedule.class);
        } catch (Exception e) {
            return defaultSchedule();
        }
    }

    /**
     * 디폴트 — 옵션 단계 없음.
     * {@link com._team._team.evaluation.scheduler.StageTransitionScheduler} 는 응답의
     * {@code SELF_SUBMITTED} 등에서 다음 phase 로 전이하므로, phase 목록은 응답 stage 명과 맞춘다.
     * (날짜 없음 → 일정 전이는 일어나지 않음. 시즌 생성 시 {@code scheduleJson} 에 날짜 포함 권장.)
     */
    public static EvaluationSchedule defaultSchedule() {
        return EvaluationSchedule.builder()
                .phases(List.of(
                        Phase.builder().stage(EvaluationStage.SELF_SUBMITTED).build(),
                        Phase.builder().stage(EvaluationStage.CALIBRATION_OPEN).build(),
                        Phase.builder().stage(EvaluationStage.CONFIRMED).build()
                ))
                .build();
    }
}
