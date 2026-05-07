package com._team._team.attendance.domain.enums;

/**
 * 스케줄 선택 결재 상태
 * PENDING    결재 요청 제출, 승인 대기 중. Resolver는 이 상태를 조회하지 않음
 * APPROVED   승인 완료, 실제 근무 스케줄로 반영
 * REJECTED   반려됨, 이후 재제출 가능
 * CANCELLED  본인 또는 관리자가 취소
 * AUTO       시스템 자동 할당 (입사 기본 스케줄, 마감일 미선택자)
 */
public enum ScheduleApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    AUTO
}
