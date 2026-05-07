package com._team._team.salary.repository;

import com._team._team.salary.domain.SalaryNegotiation;
import com._team._team.salary.domain.enums.NegotiationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryNegotiationRepository extends JpaRepository<SalaryNegotiation, UUID> {

    /** 단건 조회 회사 + 삭제되지 않은 것 */
    Optional<SalaryNegotiation> findByNegotiationIdAndCompanyIdAndDelYn(
            UUID negotiationId, UUID companyId, String delYn);

    /** 회사 단위 협상 목록 최신 등록 순 */
    @Query("SELECT n FROM SalaryNegotiation n " +
            "WHERE n.companyId = :companyId AND n.delYn = 'N' " +
            "ORDER BY n.createdAt DESC")
    List<SalaryNegotiation> findByCompanyId(@Param("companyId") UUID companyId);

    /** 같은 시즌 묶음 조회 (정기 시즌 보드용) */
    @Query("SELECT n FROM SalaryNegotiation n " +
            "WHERE n.companyId = :companyId AND n.groupId = :groupId AND n.delYn = 'N' " +
            "ORDER BY n.createdAt ASC")
    List<SalaryNegotiation> findByCompanyIdAndGroupId(
            @Param("companyId") UUID companyId, @Param("groupId") UUID groupId);

    /** 직원 본인 협상 목록 */
    @Query("SELECT n FROM SalaryNegotiation n " +
            "WHERE n.companyId = :companyId AND n.memberId = :memberId AND n.delYn = 'N' " +
            "ORDER BY n.createdAt DESC")
    List<SalaryNegotiation> findByCompanyIdAndMemberId(
            @Param("companyId") UUID companyId, @Param("memberId") UUID memberId);

    /** 상태별 카운트 (대시보드용) */
    @Query("SELECT n.status AS status, COUNT(n) AS cnt FROM SalaryNegotiation n " +
            "WHERE n.companyId = :companyId AND n.delYn = 'N' " +
            "GROUP BY n.status")
    List<Object[]> countByStatus(@Param("companyId") UUID companyId);

    /** 상태 필터 단일 */
    List<SalaryNegotiation> findByCompanyIdAndStatusAndDelYn(
            UUID companyId, NegotiationStatus status, String delYn);
}
