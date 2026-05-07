package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.enums.EvalType;
import com._team._team.evaluation.domain.enums.EvaluationStatus;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.service.scoring.ScoreBreakdown;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ResponseResDto {
    private UUID responseId;
    private UUID companyId;
    /** 평가 그룹이 속한 시즌 — 클라이언트가 그룹/설계 조회 시 경로 확보용 */
    private UUID seasonId;
    /** 시즌 이름 — 피평가자 결과 화면에서 컨텍스트 제공용 (EVALUATION:READ 권한 없는 사원도 필요) */
    private String seasonName;
    private SeasonStatus seasonStatus;
    /** 시즌의 결과 공개 시각 — null 이면 비공개 */
    private LocalDateTime seasonResultsPublishedAt;
    private UUID groupId;
    /** 그룹에 연결된 평가 설계 — 작성 화면에서 문항 로드용 */
    private UUID designId;
    private UUID targetMemberId;
    /** 대상자 이름 — member-service Feign 조회로 채워짐 (장애 시 null) */
    private String targetMemberName;
    /** 대상자 소속 조직명 */
    private String targetMemberDepartment;
    /** 대상자 프로필 이미지 URL */
    private String targetMemberProfileUrl;
    private UUID evaluatorId;
    /** 평가자 이름 — member-service Feign 조회로 채워짐 (장애 시 null) */
    private String evaluatorName;
    /** 평가자 소속 조직명 */
    private String evaluatorDepartment;
    /** 평가자 프로필 이미지 URL */
    private String evaluatorProfileUrl;
    private EvalType evaluationType;
    private EvaluationStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime lastRemindedAt;
    private String answersJson;
    private String calibrationJson;
    /** 캘리브레이션 정규화 점수 (0~100) — 결과 분석·결과 화면에서 사용 */
    private BigDecimal normalizedScore;
    /** 평가 시즌 시작 시점에 캡처된 타깃 목표 스냅샷 (List&lt;GoalSnapshotDto&gt; 직렬화). */
    private String goalSnapshotJson;
    private String confirmedGrade;
    private BigDecimal finalScoreSnapshot;
    private UUID confirmedBy;
    private LocalDateTime confirmedAt;
    // [D-5] targetGoalIdsJson 제거 — goalSnapshotJson 안에 goalId 가 이미 포함됨

    /**
     * [L-1] 섹션 타입별 채점 breakdown — 결과 화면에서 KPI 기여도 시각화용.
     * 저장하지 않고 조회 시점에 재계산된다. null 이면 아직 채점 불가/설계 없음.
     */
    private ScoreBreakdown scoreBreakdown;

    public static ResponseResDto from(EvaluationResponse e) {
        return ResponseResDto.builder()
                .responseId(e.getResponseId())
                .companyId(e.getCompanyId())
                .seasonId(e.getGroup().getSeason().getSeasonId())
                .seasonName(e.getGroup().getSeason().getName())
                .seasonStatus(e.getGroup().getSeason().getStatus())
                .seasonResultsPublishedAt(e.getGroup().getSeason().getResultsPublishedAt())
                .groupId(e.getGroup().getGroupId())
                .designId(e.getGroup().getDesign() != null ? e.getGroup().getDesign().getDesignId() : null)
                .targetMemberId(e.getTargetMemberId())
                .evaluatorId(e.getEvaluatorId())
                .evaluationType(e.getEvaluationType())
                .status(e.getStatus())
                .submittedAt(e.getSubmittedAt())
                .lastRemindedAt(e.getLastRemindedAt())
                .answersJson(e.getAnswersJson())
                .calibrationJson(e.getCalibrationJson())
                .normalizedScore(e.getNormalizedScore())
                .goalSnapshotJson(e.getGoalSnapshotJson())
                .confirmedGrade(e.getConfirmedGrade())
                .finalScoreSnapshot(e.getFinalScoreSnapshot())
                .confirmedBy(e.getConfirmedBy())
                .confirmedAt(e.getConfirmedAt())
                .build();
    }
}
