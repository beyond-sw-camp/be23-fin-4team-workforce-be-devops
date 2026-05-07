package com._team._team.evaluation.domain.enums;

/**
 * 평가 시즌의 세부 진행 페이즈.
 * SeasonStatus 는 상위 상태(DRAFT/ACTIVE/CLOSED) 이고,
 * phase 는 ACTIVE 상태에서의 내부 진행 단계를 나타냅니다.
 *
 * 전이 규칙 (선형 진행):
 * NOT_STARTED
 *      ↓ startSelfEval
 * SELF_EVAL
 *      ↓ startPeerEval
 * PEER_EVAL
 *      ↓ startUpwardEval
 * UPWARD_EVAL
 *      ↓ startDownwardEval
 * DOWNWARD_EVAL
 *      ↓ startCalibration
 * CALIBRATION
 *      ↓ confirmCalibration
 * CONFIRMED
 *      ↓ publishResults
 * PUBLISHED
 */

public enum SeasonPhase {
    /** 시즌 활성화만 되고 평가 단계는 시작되지 않음 */
    NOT_STARTED,
    /** 자기 평가 단계 */
    SELF_EVAL,
    /** 동료(다면) 평가 단계 */
    PEER_EVAL,
    /** 상향 평가 단계 */
    UPWARD_EVAL,
    /** 하향(관리자) 평가 단계 */
    DOWNWARD_EVAL,
    /** 캘리브레이션 조정 단계 */
    CALIBRATION,
    /** 캘리브레이션 확정 완료 */
    CONFIRMED,
    /** 구성원에게 결과 공개 */
    PUBLISHED
}
