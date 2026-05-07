package com._team._team.contract.repository;

import com._team._team.contract.domain.ContractBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractBatchRepository extends JpaRepository<ContractBatch, UUID> {
    List<ContractBatch> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
