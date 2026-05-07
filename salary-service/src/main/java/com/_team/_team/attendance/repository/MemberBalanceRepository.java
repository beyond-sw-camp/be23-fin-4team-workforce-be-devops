package com._team._team.attendance.repository;

import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/**
 * 휴가 잔여 Repository
 * - 동시 차감 방지를 위한 비관적 락 메서드 포함
 * [동시성 시나리오]
 * 1. 직원 A가 연차 1일 신청 → 승인 처리 중 (remaining: 5.0)
 * 2. 같은 순간 직원 A의 다른 연차 1일도 승인 처리
 * 3. 락 없으면: 둘 다 remaining=5.0 읽음 → 둘 다 4.0으로 갱신 → 1일 증발
 * 4. 락 있으면: 첫 번째가 5.0→4.0, 두 번째가 4.0→3.0 순차 처리
 */

@Repository
public interface MemberBalanceRepository extends JpaRepository<MemberBalance, UUID> {

    /** 직원의 전체 휴가 잔여 조회 (마이페이지용) */
    List<MemberBalance> findByMemberIdAndCompanyIdAndDelYn(
            UUID memberId, UUID companyId, String delYn);

    /**
     * 차감용 조회 (비관적 락)
     * - 트랜잭션 끝날 때까지 다른 트랜잭션 대기
     * (조회하는 순간 행을 잠금. 다른 트랜잭션은 읽지 못하고 대기.)
     * 연차는 숫자 차감, 재시도로 실패할 수 있는 낙관적 락보다, 잠깐 대기하더라도 반드시 순차 처리되는 비관적 락이 맞음.
     * 실제로 연차 차감 동시 요청이 빈번하진 않으니 대기 시간도 거의 없는 것으로 예상
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.memberId = :memberId " +
            "AND mb.balanceType = :type " +
            "AND mb.isUsableYn = 'Y' " +
            "AND mb.isExpireYn = 'N' " +
            "AND mb.delYn = 'N'")
    Optional<MemberBalance> findWithLock(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("type") BalanceType type);

    /**
     * 연차 잔여일수 읽기 전용 조회 (급여 계산용)
     * - 급여 계산 시에는 잔여일수를 "읽기만" 함 (차감 안 함)
     * - 연차미사용수당은 연 1회 정산
     */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.memberId = :memberId " +
            "AND mb.balanceType = 'ANNUAL' " +
            "AND mb.isUsableYn = 'Y' " +
            "AND mb.isExpireYn = 'N' " +
            "AND mb.delYn = 'N'")
    Optional<MemberBalance> findAnnualBalance(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId);

    /**
     * [배치 중복 방지] 특정 사원에게 특정 기간 만료 예정인 해당 타입 휴가 잔여일이 이미 있는지 체크
     */
    @Query("SELECT COUNT(mb) > 0 FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.memberId = :memberId " +
            "AND mb.balanceType = :type " +
            "AND mb.expirationDate BETWEEN :from AND :to " +
            "AND mb.delYn = 'N'")
    boolean existsInExpirationRange(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("type") BalanceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 아직 만료 처리 안 된 휴가 잔여일 조회
     */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.expirationDate < :baseDate " +
            "AND mb.isExpireYn = 'N' " +
            "AND mb.delYn = 'N'")
    List<MemberBalance> findExpirationTargets(
            @Param("baseDate") LocalDate baseDate);

    /**
     * [배치 최적화] 이미 부여된 사원 ID 집합 조회
     * - 회사 단위로 한 번만 SELECT → 루프 안에서 O(1) 중복 체크용
     * - 같은 companyId + balanceType + expirationDate 범위 내에 이미 있는 memberId 만 모음
     */
    @Query("SELECT mb.memberId FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.balanceType = :type " +
            "AND mb.expirationDate BETWEEN :from AND :to " +
            "AND mb.delYn = 'N'")
    Set<UUID> findGrantedMemberIds(
            @Param("companyId") UUID companyId,
            @Param("type") BalanceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 회사 내 활성 MONTHLY 잔액 엔티티 조회
     */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.balanceType = 'MONTHLY' " +
            "AND mb.isExpireYn = 'N' " +
            "AND mb.delYn = 'N'")
    List<MemberBalance> findMonthlyBalancesByCompany(
            @Param("companyId") UUID companyId);

    /**
     * [이월 배치용] 특정 회사 + 특정 타입 + 만료일 범위의 활성 잔액 목록
     * CarryoverLeaveWorker 가 전년도 ANNUAL 을 조회할 때 사용
     */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.balanceType = :type " +
            "AND mb.expirationDate BETWEEN :from AND :to " +
            "AND mb.isExpireYn = 'N' " +
            "AND mb.delYn = 'N'")
    List<MemberBalance> findActiveBalancesByCompanyAndTypeAndExpiration(
            @Param("companyId") UUID companyId,
            @Param("type") BalanceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * [미사용수당 API 용] 만료 여부 무관하게 조회
     */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.balanceType = :type " +
            "AND mb.expirationDate BETWEEN :from AND :to " +
            "AND mb.delYn = 'N'")
    List<MemberBalance> findBalancesByCompanyAndTypeAndExpirationIncludingExpired(
            @Param("companyId") UUID companyId,
            @Param("type") BalanceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** 특정 사원의 특정 타입 활성 잔액 1건 */
    @Query("SELECT mb FROM MemberBalance mb " +
            "WHERE mb.companyId = :companyId " +
            "AND mb.memberId = :memberId " +
            "AND mb.balanceType = :type " +
            "AND mb.expirationDate BETWEEN :from AND :to " +
            "AND mb.delYn = 'N'")
    Optional<MemberBalance> findBalanceByMemberAndTypeAndExpiration(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("type") BalanceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 만료 대상 연차 조회
     */
    @Query("""
       SELECT b FROM MemberBalance b
       WHERE b.expirationDate < :today
         AND b.isExpireYn = 'N'
       """)
    List<MemberBalance> findAllToExpire(@Param("today") LocalDate today);

    // 휴가 신청 시 휴가 잔고 사전 체크
    Optional<MemberBalance> findByCompanyIdAndMemberIdAndBalanceTypeAndDelYn(
            UUID companyId, UUID memberId, BalanceType balanceType, String delYn);

    /**
     * 연차사용촉진제 대상 잔고 조회
     * 회사 소속 ANNUAL 타입이면서 만료일이 targetDate 이고 잔여일수 > 0
     */
    @Query("""
   SELECT b FROM MemberBalance b
   WHERE b.companyId = :companyId
     AND b.balanceType = 'ANNUAL'
     AND b.expirationDate = :targetDate
     AND b.remaining > 0
     AND b.isExpireYn = 'N'
     AND b.delYn = 'N'
   """)
    List<MemberBalance> findForPromotion(
            @Param("companyId") UUID companyId,
            @Param("targetDate") LocalDate targetDate);

    /**
     * 오늘 만료될 연차 잔고 + 잔여 > 0 (전 회사 일괄 조회)
     */
    @Query("""
   SELECT b FROM MemberBalance b
   WHERE b.expirationDate = :today
     AND b.balanceType = 'ANNUAL'
     AND b.remaining > 0
     AND b.isExpireYn = 'N'
     AND b.delYn = 'N'
   """)
    List<MemberBalance> findExpiringAnnualWithRemaining(@Param("today") LocalDate today);

    /**
     * 퇴직 cascade - 직원의 모든 활성 잔액을 사용 불가(isUsableYn='N') 처리
     * remaining 값은 보존 (미사용 연차 수당 환산 시 참조)
     */
    @Modifying
    @Query("""
       UPDATE MemberBalance b
          SET b.isUsableYn = 'N'
        WHERE b.memberId = :memberId
          AND b.companyId = :companyId
          AND b.isUsableYn = 'Y'
          AND b.delYn = 'N'
       """)
    int markAllUnusableByMember(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId);

    /**
     * 퇴직 시점의 직원별 활성 ANNUAL 잔액 합 (미사용 연차 수당 정산용 정보)
     * 비활성화 처리 전에 호출하여 로그/리포트에 잔여 일수 기록
     */
    @Query("""
       SELECT COALESCE(SUM(b.remaining), 0)
         FROM MemberBalance b
        WHERE b.memberId = :memberId
          AND b.companyId = :companyId
          AND b.balanceType = :type
          AND b.isUsableYn = 'Y'
          AND b.isExpireYn = 'N'
          AND b.delYn = 'N'
       """)
    Double sumRemainingByMemberAndType(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("type") BalanceType type);
}
