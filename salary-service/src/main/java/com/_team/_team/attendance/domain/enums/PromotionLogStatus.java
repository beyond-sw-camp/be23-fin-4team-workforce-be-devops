package com._team._team.attendance.domain.enums;

/**
 * 연차사용촉진 알림 이력 상태
 * SENT         : 발송 완료, 직원 응답 대기
 * ACKNOWLEDGED : 직원이 사용계획 회신 (촉진 의무 이행 완료)
 * DESIGNATED   : 회사가 노무수령 거부, 강제 연차일 지정
 */
public enum PromotionLogStatus {
    SENT,
    ACKNOWLEDGED,
    DESIGNATED
}