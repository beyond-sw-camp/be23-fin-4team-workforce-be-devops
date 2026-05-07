package com._team._team.evaluation.domain.enums;

/**
 * 평가 응답 단계머신.
 *
 *  SELF_PENDING        : 자기평가 입력 중
 *  SELF_SUBMITTED      : 자기평가 제출 완료 (selfEvalEmpty=true 가능 — 마감 후 자동전이)
 *  CALIBRATION_OPEN    : Lead/Assistant 가 등급 조정 입력 가능
 *  CALIBRATION_LOCKED  : Lead 가 finalGrade 입력 후 잠금
 *  CONFIRMED           : 최종 등급 확정 + finalScoreSnapshot 산출
 *  SKIPPED_LEAVER      : 퇴사 등으로 평가 제외
 *
 *  옵션 단계 (시즌 scheduleJson 에 포함된 경우만):
 *    PEER_OPEN, UPWARD_OPEN, DOWNWARD_OPEN — SELF_SUBMITTED 와 CALIBRATION_OPEN 사이에 삽입
 */
public enum EvaluationStage {
    SELF_PENDING,
    SELF_SUBMITTED,
    PEER_OPEN,
    UPWARD_OPEN,
    DOWNWARD_OPEN,
    CALIBRATION_OPEN,
    CALIBRATION_LOCKED,
    CONFIRMED,
    SKIPPED_LEAVER
}
