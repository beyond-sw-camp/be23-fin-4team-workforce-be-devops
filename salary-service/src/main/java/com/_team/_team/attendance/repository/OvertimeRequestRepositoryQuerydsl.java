package com._team._team.attendance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * OvertimeRequest 동적 검색 / 집계 (QueryDSL)
 */
public interface OvertimeRequestRepositoryQuerydsl {

    /**
     * 기간 내 직원별 승인된 초과근무시간(분) 합계
     * - 포괄임금제 초과 근무 현황 화면에서 사용
     */
    List<MemberApprovedMinutesRow> sumApprovedMinutesByCompanyAndRange(
            UUID companyId, LocalDate from, LocalDate to);

    /**
     * 그룹 결과를 담는 단순 record - 직원당 1행
     */
    record MemberApprovedMinutesRow(UUID memberId, long sumMinutes) {}
}
