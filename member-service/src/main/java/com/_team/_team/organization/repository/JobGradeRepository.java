package com._team._team.organization.repository;

import com._team._team.organization.domain.JobGrade;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobGradeRepository extends JpaRepository<JobGrade, UUID> {
    List<JobGrade> findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
            UUID companyId, String delYn);

    // displayOrder용 - 동시성 이슈 해결
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM JobGrade j " +
            "WHERE j.company.companyId = :companyId " +
            "AND j.delYn = :delYn " +
            "ORDER BY j.displayOrder")
    List<JobGrade> findByCompanyIdAndDelYnWithLock(
            @Param("companyId") UUID companyId,
            @Param("delYn") String delYn);

    boolean existsByCompany_CompanyIdAndDisplayOrderAndDelYn(
            UUID companyId, int displayOrder, String delYn);
}
