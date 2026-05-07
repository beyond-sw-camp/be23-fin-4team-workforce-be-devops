package com._team._team.contract.repository;

import com._team._team.contract.domain.ContractTemplate;
import com._team._team.contract.domain.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {
    List<ContractTemplate> findByCompanyIdAndDelYn(UUID companyId, String delYn);

    List<ContractTemplate> findByCompanyIdAndIsActiveYnAndDelYn(UUID companyId, String isActiveYn, String delYn);

    List<ContractTemplate> findByCompanyIdAndContractTypeAndDelYn(UUID companyId, ContractType contractType, String delYn);
}
