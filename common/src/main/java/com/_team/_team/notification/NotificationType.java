package com._team._team.notification;

public enum NotificationType {

    // 근태
    ATTENDANCE_MODIFIED,         // 근태 수정
    ATTENDANCE_LATE,             // 지각 의심
    ATTENDANCE_EARLY_LEAVE,      // 조퇴 의심
    ATTENDANCE_MISSING_CLOCK_OUT,// 미퇴근
    ATTENDANCE_ABSENT_SUSPECT,   // 결근 의심
    ATTENDANCE_LEAVE_CLOCKIN,    // 휴가일 출근
    LEAVE_REQUESTED,         // 연차 신청
    LEAVE_APPROVED,          // 연차 승인
    LEAVE_REJECTED,          // 연차 반려
     LABOR_LAW_WEEKLY_VIOLATION,  // 주간 (52시간) 초과근로 시간 위반

    // 연차 사용 촉진 (근기법 61조)
    LEAVE_PROMOTION_FIRST,    // 연차 촉진 1차 (만료 N일 전)
    LEAVE_PROMOTION_SECOND,   // 연차 촉진 2차 (만료 M일 전)
    LEAVE_DESIGNATION,   // 회사 강제 지정 통지 노무수령 거부


    // 결재
    APPROVAL_REQUESTED,      // 결재 요청
    APPROVAL_APPROVED,       // 결재 승인
    APPROVAL_REJECTED,       // 결재 반려
    APPROVAL_CANCELED,      // 결재 회수됨
    APPROVAL_REFERENCED,     // 결재 참조자
    APPROVAL_CIRCULATED,     // 결재 공람자
    APPROVAL_PROXY_ASSIGNED,  // 결재 위임 지정됨
    APPROVAL_PROXY_ACTED,     // 대결 처리됨 (부재자에게)

    // 전자계약
    CONTRACT_SENT,           // 계약서 발송됨 (직원에게)
    CONTRACT_SIGNED,         // 계약서 서명 완료 (인사팀에게)
    CONTRACT_REJECTED,       // 계약서 거절됨 (인사팀에게)
    CONTRACT_CANCELED,       // 계약서 회수됨 (직원에게)
    CONTRACT_REMIND,       // 계약 서명 리마인드

    // 급여
    SALARY_PUBLISHED,        // 급여 명세서 발행 (확정)
    SALARY_PAID,             // 급여 지급 완료 (계좌 입금)
    BONUS_PAYMENT,           // 정기 상여 지급일 알림

    // 목표
    GOAL_EVALUATED,               // 목표 평가 완료
    GOAL_BUNDLE_REQUESTED,        // 목표 승인 번들 요청
    GOAL_BUNDLE_APPROVED,         // 목표 승인 번들 승인
    GOAL_BUNDLE_REJECTED,         // 목표 승인 번들 반려
    GOAL_BUNDLE_WITHDRAWN,        // 목표 승인 번들 회수
    GOAL_BUNDLE_DELEGATED,        // 목표 승인 번들 위임

    // 평가
    EVALUATION_REMINDER,     // 미제출 평가 리마인드
    EVALUATION_REOPENED,     // 제출된 평가 재작성 허용

    // 인사
    MEMBER_DORMANT,          // 휴직 처리
    MEMBER_RETURN,            // 복직 처리

    //ESG
    ESG_ACTIVITY_APPROVED,   // 활동 승인됨
    ESG_ACTIVITY_REJECTED,   // 활동 반려됨
    ESG_POINT_EARNED,        // 포인트 적립됨
    ESG_CAMPAIGN_STARTED,    // 캠페인 시작
    ESG_CAMPAIGN_CLOSED,     // 캠페인 종료
    ESG_SHOP_ORDER_COMPLETE, // 물품 구매 완료

    // 캘린더
    CALENDAR_TEAM_EVENT_CREATED,  // 팀 일정 등록됨

    // 직원 관리
    MEMBER_ROLE_CHANGED,     // 역할 변경됨
    MEMBER_INFO_UPDATED,     // 인사 정보 수정됨
}

