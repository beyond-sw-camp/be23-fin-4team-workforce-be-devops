package com._team._team.evaluation.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.domain.enums.SeasonType;
import com._team._team.goal.domain.enums.KpiCycle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EvaluationSeason (확장)
 *
 *  추가:
 *   - targetCycle : 이 시즌이 평가할 목표 cycle 종류 (QUARTERLY/HALF_YEARLY/YEARLY)
 *                   목표 cycle ≠ 평가 cycle 분리 원칙
 *
 *  ACTIVE 전이 시점에:
 *   - 대상 멤버 EvaluationResponse 자동 생성
 *   - goalSnapshotJson 봉인
 *   - 가중치 100% 미달 멤버 존재 시 SeasonActivationBlockedException
 *   - 모든 ACTIVE goal → COMPLETED 자동
 */
@Entity
@Table(name = "evaluation_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationSeason extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SeasonType type;

    /** 신규 — 평가 대상 목표의 cycle 종류 */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_cycle", length = 32)
    private KpiCycle targetCycle;

    /** 신규 — 평가 대상 cycle 의 시작일 (예: 2026-04-01 = 2026-Q2). goal 조회 키. */
    @Column(name = "target_cycle_start")
    private LocalDate targetCycleStart;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private SeasonStatus status = SeasonStatus.DRAFT;

    @Column(name = "result_publish_date")
    private LocalDate resultPublishDate;

    @Column(name = "results_published_at")
    private LocalDateTime resultsPublishedAt;

    @Column(name = "schedule_json", columnDefinition = "JSON")
    private String scheduleJson;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    public void openSelfEval() {
        require(SeasonStatus.DRAFT, "Only draft seasons can open self evaluation.");
        this.status = SeasonStatus.SELF_EVAL;
    }

    public void openManagerEval() {
        require(SeasonStatus.SELF_EVAL, "Manager evaluation can open only after self evaluation.");
        this.status = SeasonStatus.MANAGER_EVAL;
    }

    public void openGradeConfirm() {
        require(SeasonStatus.MANAGER_EVAL, "Grade confirmation can open only after manager evaluation.");
        this.status = SeasonStatus.GRADE_CONFIRM;
    }

    public void publishResults() {
        require(SeasonStatus.GRADE_CONFIRM, "Results can be published only after grade confirmation.");
        this.status = SeasonStatus.RESULT_PUBLISHED;
        markResultsPublished();
    }

    public void openInterview() {
        require(SeasonStatus.RESULT_PUBLISHED, "Interview can open only after result publication.");
        this.status = SeasonStatus.INTERVIEW;
    }

    public void close() {
        require(SeasonStatus.INTERVIEW, "Seasons can be closed only after interview.");
        this.status = SeasonStatus.CLOSED;
    }

    public void markResultsPublished() {
        if (this.resultsPublishedAt == null) {
            this.resultsPublishedAt = LocalDateTime.now();
        }
    }

    public boolean isResultsPublished() {
        return this.resultsPublishedAt != null;
    }

    public boolean isResponseEditable() {
        return isSelfEvalEditable() || isManagerEvalEditable();
    }

    public boolean isSelfEvalEditable() {
        return this.status == SeasonStatus.SELF_EVAL;
    }

    public boolean isManagerEvalEditable() {
        return this.status == SeasonStatus.MANAGER_EVAL;
    }

    public boolean canCalibrate() {
        return this.status == SeasonStatus.GRADE_CONFIRM;
    }

    public boolean isResultVisible() {
        return this.status == SeasonStatus.RESULT_PUBLISHED
                || this.status == SeasonStatus.INTERVIEW
                || this.status == SeasonStatus.CLOSED;
    }

    public void update(String name,
                       SeasonType type,
                       KpiCycle targetCycle,
                       LocalDate targetCycleStart,
                       LocalDate startDate,
                       LocalDate endDate,
                       LocalDate resultPublishDate,
                       String scheduleJson) {
        this.name = name;
        this.type = type;
        this.targetCycle = targetCycle;
        this.targetCycleStart = targetCycleStart;
        this.startDate = startDate;
        this.endDate = endDate;
        this.resultPublishDate = resultPublishDate;
        this.scheduleJson = scheduleJson;
    }

    private void require(SeasonStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
