package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.enums.EvaluationStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 결과 공개(publishResults) 차단 사유 상세.
 *
 *  자동/수동 공개 시 미확정 응답이 1건이라도 있으면 공개 거부.
 *  운영자(HR/평가관리자)는 본 DTO 로 어떤 응답이 어느 단계에서 막혀 있는지 확인.
 *
 *  blockable = (전체 응답 - CONFIRMED - SKIPPED_LEAVER)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishBlockersResDto {

    private UUID seasonId;
    private int totalResponses;
    /** 모든 stage 별 카운트 (0 포함) */
    private Map<String, Integer> byStage;
    /** 미확정 응답 상세 — 운영자 후속 조치용 */
    private List<BlockerEntry> blockers;
    /** 공개 가능 여부 */
    private boolean publishable;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlockerEntry {
        private UUID responseId;
        private UUID targetMemberId;
        private UUID evaluatorId;
        private EvaluationStage stage;
    }
}
