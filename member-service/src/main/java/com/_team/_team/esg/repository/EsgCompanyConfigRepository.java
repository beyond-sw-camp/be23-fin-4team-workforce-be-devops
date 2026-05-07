package com._team._team.esg.repository;

import com._team._team.company.domain.Company;
import com._team._team.esg.domain.EsgCompanyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgCompanyConfigRepository extends JpaRepository<EsgCompanyConfig, UUID> {

    Optional<EsgCompanyConfig> findByCompany(Company company);

    boolean existsByCompany(Company company);
}
