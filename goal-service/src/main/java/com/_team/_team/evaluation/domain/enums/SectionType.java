package com._team._team.evaluation.domain.enums;

/**
 * 평가 설계 섹션 타입.
 * [L-1 Step 1] 섹션 유형을 enum 으로 분리. 스코어링 로직은 Phase C 에서 연결.
 *
 *  - MANUAL        : 기존 수동 입력형 섹션 (answersJson 의 answers 기반)
 *  - KPI_SCORE     : KPI 달성률(goalSnapshot.achievementPctAtSnapshot) 자동 반영 섹션
 *  - PEER_FEEDBACK : 동료 피드백 집계 섹션 (서술 + 평균)
 *
 *  기본값은 MANUAL — 기존 설계 JSON 에 type 필드가 없을 때 사용.
 */
public enum SectionType {
    MANUAL,
    KPI_SCORE,
    PEER_FEEDBACK
}
