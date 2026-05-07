package com._team._team.salary.domain.enums;

/**
 * 연봉 협상 상태 전이 (양방 합의 흐름)
 *  DRAFT       관리자 입력 중
 *  SUBMITTED   직원 검토 대기 (관리자 제안 -> 직원 수락/거절 응답 기다림)
 *  APPROVED    직원 수락 -> 적용 대기
 *  REJECTED    직원 거절 종료
 *  APPLIED     Salary 새 행 생성 적용 완료 잠금
 */
public enum NegotiationStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    APPLIED
}
