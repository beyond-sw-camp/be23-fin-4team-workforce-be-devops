package com._team._team.salary.repository;

import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberAllowanceRepository extends JpaRepository<MemberAllowance, UUID> {


    // 급여 계산 시 호출, 특정 일자에 활성 수당 전체
    @Query("""
           SELECT a FROM MemberAllowance a
           WHERE a.memberId = :memberId
             AND a.companyId = :companyId
             AND a.effectiveFrom <= :date
             AND (a.effectiveTo IS NULL OR a.effectiveTo >= :date)
             AND a.approvalStatus IN ('APPROVED', 'AUTO')
             AND a.delYn = 'N'
           """)
    List<MemberAllowance> findActiveByMemberAndDate(@Param("memberId") UUID memberId,
                                                    @Param("companyId") UUID companyId,
                                                    @Param("date") LocalDate date);

    /**
     * 특정 수당 항목의 활성 건 조회 - 변경/종료 대상 탐색용.
     * 정상 데이터라면 최대 1건이지만, 과거 버그로 중복 row 가 쌓일 수 있으므로 List 반환으로
     * 모든 활성 행을 함께 처리할 수 있게 한다 (Optional 로 받으면 NonUniqueResult 예외 발생).
     */
    @Query("""
           SELECT a FROM MemberAllowance a
           WHERE a.memberId = :memberId
             AND a.companyId = :companyId
             AND a.salaryItemTemplateId = :templateId
             AND (a.effectiveTo IS NULL OR a.effectiveTo >= :date)
             AND a.approvalStatus IN ('APPROVED', 'AUTO')
             AND a.delYn = 'N'
           ORDER BY a.effectiveFrom DESC
           """)
    List<MemberAllowance> findCurrentByTemplate(@Param("memberId") UUID memberId,
                                                @Param("companyId") UUID companyId,
                                                @Param("templateId") UUID templateId,
                                                @Param("date") LocalDate date);

    // 결재 이벤트 수신 시 연결된 레코드 조회
    Optional<MemberAllowance> findByApprovalRequestId(UUID approvalRequestId);

    // 개인 수당 이력 전체
    List<MemberAllowance> findAllByMemberIdAndCompanyIdOrderByEffectiveFromDesc(
            UUID memberId, UUID companyId);

    // 직원 본인 현재 수당 목록
    List<MemberAllowance> findByMemberIdAndCompanyIdAndDelYnOrderByEffectiveFromDesc(
            UUID memberId, UUID companyId, String delYn);

    // 관리자 결재 대기 목록
    List<MemberAllowance> findAllByCompanyIdAndApprovalStatusOrderByRequestedAtAsc(
            UUID companyId, AllowanceApprovalStatus status);

    // 회사 전체 수당 상태별 조회 (관리자)
    List<MemberAllowance> findByCompanyIdAndApprovalStatusAndDelYnOrderByRequestedAtDesc(
            UUID companyId, AllowanceApprovalStatus status, String delYn);

    /**
     * 회사 전체 수당
     */
    @Query("""
           SELECT a FROM MemberAllowance a
            WHERE a.companyId = :companyId
              AND a.delYn = 'N'
              AND a.effectiveFrom <= :monthEnd
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :monthStart)
            ORDER BY a.requestedAt DESC
           """)
    List<MemberAllowance> findCompanyAllowancesActiveInMonth(
            @Param("companyId") UUID companyId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** 동일 조건 + 특정 status 만 조회 */
    @Query("""
           SELECT a FROM MemberAllowance a
            WHERE a.companyId = :companyId
              AND a.delYn = 'N'
              AND a.approvalStatus = :status
              AND a.effectiveFrom <= :monthEnd
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :monthStart)
            ORDER BY a.requestedAt DESC
           """)
    List<MemberAllowance> findCompanyAllowancesActiveInMonthByStatus(
            @Param("companyId") UUID companyId,
            @Param("status") AllowanceApprovalStatus status,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /** 회사 전체 이력 - 효력일 역순 */
    @Query("""
           SELECT a FROM MemberAllowance a
            WHERE a.companyId = :companyId
              AND a.delYn = 'N'
            ORDER BY a.effectiveFrom DESC, a.requestedAt DESC
           """)
    List<MemberAllowance> findCompanyAllHistory(@Param("companyId") UUID companyId);

    /**
     * 직원 1명의 활성 수당 조회
     * 결재 상태는 APPROVED / AUTO 만 (정산에 실제 반영되는 것)
     */
    @Query("""
           SELECT a FROM MemberAllowance a
            WHERE a.memberId = :memberId
              AND a.companyId = :companyId
              AND a.delYn = 'N'
              AND a.approvalStatus IN ('APPROVED', 'AUTO')
              AND a.effectiveFrom <= :monthEnd
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :monthStart)
            ORDER BY a.effectiveFrom DESC
           """)
    List<MemberAllowance> findMemberAllowancesActiveInMonth(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /**
     * 직원의 모든 활성 수당을 일괄 소프트 삭제.
     * Salary 마지막 행 삭제 시 cascade 정리용 - 정산 대상 직원이 아니게 되면
     * 남은 MemberAllowance 는 의미가 없으므로 함께 정리한다.
     */
    @Modifying
    @Query("""
           UPDATE MemberAllowance a
              SET a.delYn = 'Y'
            WHERE a.memberId = :memberId
              AND a.companyId = :companyId
              AND a.delYn = 'N'
           """)
    int softDeleteAllByMember(@Param("memberId") UUID memberId,
                              @Param("companyId") UUID companyId);

    /**
     * 특정 직원/템플릿의 모든 활성 행 일괄 소프트 삭제
     * 미래 effectiveFrom 의 수당을 토글 해제시 사용
     */
    @Modifying
    @Query("""
           UPDATE MemberAllowance a
              SET a.delYn = 'Y'
            WHERE a.memberId = :memberId
              AND a.companyId = :companyId
              AND a.salaryItemTemplateId = :templateId
              AND a.delYn = 'N'
           """)
    int softDeleteByMemberAndTemplate(@Param("memberId") UUID memberId,
                                      @Param("companyId") UUID companyId,
                                      @Param("templateId") UUID templateId);

    /**
     * 직원의 활성 수당 effectiveTo 를 일괄 동기화.
     * 급여(salary) 의 effectiveTo 가 변경되면 모든 활성 수당 effectiveTo 를 같은 날짜로 정렬
     */
    @Modifying
    @Query("""
           UPDATE MemberAllowance a
              SET a.effectiveTo = :newEffectiveTo
            WHERE a.memberId = :memberId
              AND a.companyId = :companyId
              AND a.delYn = 'N'
           """)
    int syncEffectiveToByMember(@Param("memberId") UUID memberId,
                                @Param("companyId") UUID companyId,
                                @Param("newEffectiveTo") LocalDate newEffectiveTo);
}
