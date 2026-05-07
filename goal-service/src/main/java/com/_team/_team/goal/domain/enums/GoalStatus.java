package com._team._team.goal.domain.enums;

/**
 * 목표 상태 (재정의).
 *  DRAFT     : 작성 중 (NOT_REQUESTED 또는 REJECTED 와 페어)
 *  PENDING   : 일괄 승인 대기 중 (Bundle PENDING 시 자동 전이)
 *  ACTIVE    : 승인 후 실행 중 (시즌 ACTIVE 전까지 재제출 가능)
 *  COMPLETED : 시즌 ACTIVE 시점에 자동 전이 (평가 진입)
 *  CANCELLED : 회수/관리자 취소
 *  SKIPPED   : 퇴사/장기 부재로 평가 제외
 */
public enum GoalStatus {
    DRAFT,
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    SKIPPED
}
