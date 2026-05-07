package com._team._team.salary.repository;

import com._team._team.salary.domain.PayGradeTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface PayGradeTableRepository extends JpaRepository<PayGradeTable, UUID> {


    /** 회사 전체 호봉표, 삭제 제외, 호봉-발효일 순 */
    @Query("""
       SELECT p FROM PayGradeTable p
       WHERE p.companyId = :companyId
         AND p.delYn = 'N'
       ORDER BY p.step, p.effectiveFrom DESC
       """)
    List<PayGradeTable> findAllByCompany(@Param("companyId") UUID companyId);

    /** 특정 일자 활성 호봉 (Salary 자동 계산용) */
    @Query("""
       SELECT p FROM PayGradeTable p
       WHERE p.companyId = :companyId
         AND p.step = :step
         AND p.delYn = 'N'
         AND p.effectiveFrom <= :date
         AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
       """)
    Optional<PayGradeTable> findActiveOn(
            @Param("companyId") UUID companyId,
            @Param("step") Integer step,
            @Param("date") LocalDate date);

    /** 같은 호봉의 활성 (effectiveTo null) 레코드, 인상 시 자동 마감용 */
    @Query("""
       SELECT p FROM PayGradeTable p
       WHERE p.companyId = :companyId
         AND p.step = :step
         AND p.delYn = 'N'
         AND p.effectiveTo IS NULL
       """)
    Optional<PayGradeTable> findActiveWithNoEnd(
            @Param("companyId") UUID companyId,
            @Param("step") Integer step);

    /** 회사 전체 활성 (effectiveTo null) 레코드, bulkCreate 에서 N+1 제거용 */
    @Query("""
       SELECT p FROM PayGradeTable p
       WHERE p.companyId = :companyId
         AND p.delYn = 'N'
         AND p.effectiveTo IS NULL
       """)
    List<PayGradeTable> findAllActiveNoEnd(@Param("companyId") UUID companyId);

    Optional<PayGradeTable> findByPayGradeTableIdAndCompanyIdAndDelYn(
            UUID payGradeTableId, UUID companyId, String delYn);
}