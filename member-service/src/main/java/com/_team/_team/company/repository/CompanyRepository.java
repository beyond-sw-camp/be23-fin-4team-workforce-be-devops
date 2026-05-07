package com._team._team.company.repository;

import com._team._team.company.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    // 사업자번호 중복 체크
    boolean existsByBusinessNumber(String businessNumber);

    // 사업자번호로 회사 조회
    Optional<Company> findByBusinessNumber(String businessNumber);

    // 회사 도메인으로 회사 조회
    Optional<Company> findByCompanyDomain(String companyDomain);
}
