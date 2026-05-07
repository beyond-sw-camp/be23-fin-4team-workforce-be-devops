package com._team._team.approval.repository;

import com._team._team.approval.domain.AbsenceProxy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AbsenceProxyRepository extends JpaRepository<AbsenceProxy, UUID> {

    // 부재자의 활성 대결 설정 조회
    List<AbsenceProxy> findByMemberIdAndIsActiveYn(UUID memberId, String isActiveYn);

    // 현재 시점 유효한 대결자 조회
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.companyId = :companyId " +
            "AND ap.memberId = :memberId " +
            "AND ap.isActiveYn = 'Y' " +
            "AND ap.startDate <= :now " +
            "AND ap.endDate >= :now")
    Optional<AbsenceProxy> findActiveProxy(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("now") LocalDateTime now);

    // 대결자로 지정된 목록 조회
    List<AbsenceProxy> findBySubstituteIdAndIsActiveYn(UUID substituteId, String isActiveYn);

    // 기간 중복 체크 (동일 부재자의 활성 위임 중 기간이 겹치는 것)
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.companyId = :companyId " +
            "AND ap.memberId = :memberId " +
            "AND ap.isActiveYn = 'Y' " +
            "AND ap.startDate < :endDate " +
            "AND ap.endDate > :startDate")
    List<AbsenceProxy> findOverlapping(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 대결자가 같은 기간에 본인도 부재자인지 체크 (체인 위임 방지)
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.companyId = :companyId " +
            "AND ap.memberId = :substituteId " +
            "AND ap.isActiveYn = 'Y' " +
            "AND ap.startDate < :endDate " +
            "AND ap.endDate > :startDate")
    List<AbsenceProxy> findSubstituteAbsence(
            @Param("companyId") UUID companyId,
            @Param("substituteId") UUID substituteId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 내가 설정한 위임 (현재 + 미래, 기간 지난 건 제외)
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.companyId = :companyId " +
            "AND ap.memberId = :memberId " +
            "AND ap.endDate >= :now " +
            "ORDER BY ap.startDate ASC")
    List<AbsenceProxy> findCurrentAndFutureByMemberId(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("now") LocalDateTime now);

    // 내가 대결자로 지정된 위임 (현재 + 미래, 기간 지난 건 제외)
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.companyId = :companyId " +
            "AND ap.substituteId = :substituteId " +
            "AND ap.endDate >= :now " +
            "ORDER BY ap.startDate ASC")
    List<AbsenceProxy> findCurrentAndFutureBySubstituteId(
            @Param("companyId") UUID companyId,
            @Param("substituteId") UUID substituteId,
            @Param("now") LocalDateTime now);

    // memberId로 현재 활성 대결자 조회 (알림용 — companyId 불필요)
    @Query("SELECT ap FROM AbsenceProxy ap " +
            "WHERE ap.memberId = :memberId " +
            "AND ap.isActiveYn = 'Y' " +
            "AND ap.startDate <= :now " +
            "AND ap.endDate >= :now")
    Optional<AbsenceProxy> findActiveProxyByMemberId(
            @Param("memberId") UUID memberId,
            @Param("now") LocalDateTime now);
}
