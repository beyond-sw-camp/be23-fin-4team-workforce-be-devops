package com._team._team.contract.repository;

import com._team._team.contract.domain.ContractParty;
import com._team._team.contract.domain.enums.SignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractPartyRepository extends JpaRepository<ContractParty, UUID> {
    List<ContractParty> findByContractContractId(UUID contractId);

    Optional<ContractParty> findByContractContractIdAndMemberId(UUID contractId, UUID memberId);

    // 해당 계약에 서명 안 한 당사자가 있는지
    boolean existsByContractContractIdAndSignStatusNot(UUID contractId, SignStatus signStatus);

    @Query("SELECT p FROM ContractParty p WHERE p.contract.contractId IN :contractIds")
    List<ContractParty> findByContractIdIn(@Param("contractIds") List<UUID> contractIds);
}
