package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResponseResDto {

    private UUID responseId;
    private UUID companyId;
    private UUID groupId;
    /** v4 — group 의 season 정보 enrichment. FE 라벨링 용이성. */
    private UUID seasonId;
    private String seasonName;
    private LocalDateTime resultsPublishedAt;
    private UUID targetMemberId;
    private UUID evaluatorId;
    private EvaluationStage stage;
    private boolean selfEvalEmpty;
    private LocalDateTime submittedAt;

    private String answersJson;
    private String goalSnapshotJson;

    private String confirmedGrade;
    private BigDecimal finalScoreSnapshot;
    private UUID confirmedBy;
    private LocalDateTime confirmedAt;
    private String objectionStatus;
    private String objectionMessage;
    private String objectionResolution;
    private LocalDateTime objectionRequestedAt;
    private LocalDateTime objectionResolvedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EvaluationResponseResDto from(EvaluationResponse r) {
        UUID seasonId = null;
        String seasonName = null;
        LocalDateTime publishedAt = null;
        if (r.getGroup() != null && r.getGroup().getSeason() != null) {
            seasonId = r.getGroup().getSeason().getSeasonId();
            seasonName = r.getGroup().getSeason().getName();
            publishedAt = r.getGroup().getSeason().getResultsPublishedAt();
        }
        return EvaluationResponseResDto.builder()
                .responseId(r.getResponseId())
                .companyId(r.getCompanyId())
                .groupId(r.getGroup() != null ? r.getGroup().getGroupId() : null)
                .seasonId(seasonId)
                .seasonName(seasonName)
                .resultsPublishedAt(publishedAt)
                .targetMemberId(r.getTargetMemberId())
                .evaluatorId(r.getEvaluatorId())
                .stage(r.getStage())
                .selfEvalEmpty(r.isSelfEvalEmpty())
                .submittedAt(r.getSubmittedAt())
                .answersJson(r.getAnswersJson())
                .goalSnapshotJson(r.getGoalSnapshotJson())
                .confirmedGrade(r.getConfirmedGrade())
                .finalScoreSnapshot(r.getFinalScoreSnapshot())
                .confirmedBy(r.getConfirmedBy())
                .confirmedAt(r.getConfirmedAt())
                .objectionStatus(r.getObjectionStatus())
                .objectionMessage(r.getObjectionMessage())
                .objectionResolution(r.getObjectionResolution())
                .objectionRequestedAt(r.getObjectionRequestedAt())
                .objectionResolvedAt(r.getObjectionResolvedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /**
     * v4 — ASSISTANT 평가자용 변환. 자기평가 원문(answersJson) 마스킹.
     *  권장안 #2: ASSISTANT 는 자기평가 원문 비노출, KR 메타데이터 + 등급 기준만 열람.
     *  goalSnapshotJson 은 KR 제목/설명/등급기준 포함 → 그대로 노출.
     */
    public static EvaluationResponseResDto fromForAssistant(EvaluationResponse r) {
        EvaluationResponseResDto dto = from(r);
        dto.setAnswersJson(null);
        // confirmedGrade / finalScoreSnapshot 는 Lead 확정 결과 — ASSISTANT 도 본인 의견 작성 시 참고 가능
        return dto;
    }
}
