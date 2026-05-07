package com._team._team.attendance.repository;

import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyAttendanceLedgerRepository extends JpaRepository<MonthlyAttendanceLedger, UUID>{


    // 급여 서비스 조회 (월별 개인 장부)
    Optional<MonthlyAttendanceLedger> findByMemberIdAndLedgerYearMonth(
            UUID memberId, String ledgerYearMonth);

    // 중복 생성 방지 (월마감 배치 사전 체크)
    boolean existsByMemberIdAndLedgerYearMonth(
            UUID memberId, String ledgerYearMonth);

    // 회사 월별 전체 장부 (관리자 리포트)
    Page<MonthlyAttendanceLedger> findAllByCompanyIdAndLedgerYearMonth(
            UUID companyId, String ledgerYearMonth, Pageable pageable);

    // 재마감 대상 (isLocked=false 인 것만 수정 가능)
    List<MonthlyAttendanceLedger> findAllByCompanyIdAndLedgerYearMonthAndIsLocked(
            UUID companyId, String ledgerYearMonth, Boolean isLocked);

    // 급여 지급 완료된 잠금 장부
    Optional<MonthlyAttendanceLedger> findByMemberIdAndLedgerYearMonthAndIsLocked(
            UUID memberId, String ledgerYearMonth, Boolean isLocked);

    // 개인 월별 이력
    List<MonthlyAttendanceLedger> findAllByMemberIdOrderByLedgerYearMonthDesc(UUID memberId);

}
