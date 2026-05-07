package com._team._team.organization.repository;

import com._team._team.company.domain.Company;
import com._team._team.organization.domain.Organization;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    List<Organization> findByCompany_CompanyIdAndDelYn(UUID companyId, String delYn);

    @Query("SELECT DISTINCT o FROM Organization o " +
            "LEFT JOIN FETCH o.children c " +
            "WHERE o.company = :company " +
            "AND o.delYn = :delYn " +
            "AND o.parent IS NULL " +
            "AND (c IS NULL OR c.delYn = :delYn) " +
            "ORDER BY o.displayOrder")
    List<Organization> findByCompanyWithChildren(
            @Param("company") Company company,
            @Param("delYn") String delYn);

    // displayOrder용 - 동시성 이슈 해결
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o " +
            "WHERE o.company = :company " +
            "AND o.delYn = :delYn " +
            "ORDER BY o.displayOrder")
    List<Organization> findByCompanyAndDelYnOrderByDisplayOrderWithLock(
            @Param("company") Company company,
            @Param("delYn") String delYn);

    List<Organization> findByCompanyAndDelYnOrderByDisplayOrder(
            Company company, String delYn);
}
