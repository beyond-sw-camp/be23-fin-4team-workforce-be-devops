package com._team._team.attendance.domain.enums;

// 일마감 단계 상태
// OPEN          당일 진행 중, 출퇴근 이벤트 계속 수집
// DRAFT         02:00 배치가 임시 마감, 관리자 검토 대기
// UNDER_REVIEW  관리자 검토 시작
// FINALIZED     관리자 확정, 급여 계산 반영 가능
// LOCKED        월마감 후 잠금, 수정 불가
public enum ClosureStatus {
    OPEN,
    DRAFT,
    UNDER_REVIEW,
    FINALIZED,
    LOCKED
}
