package com._team._team.contract.repository;

import com._team._team.contract.domain.Contract;
import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.domain.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    // 직원 본인 계약 목록
    List<Contract> findByEmployeeMemberIdAndDelYnOrderByCreatedAtDesc(UUID memberId, String delYn);


    // 배치별 계약 목록
    List<Contract> findByContractBatchBatchIdAndDelYn(UUID batchId, String delYn);

    // 배치별 상태 필터
    List<Contract> findByContractBatchBatchIdAndContractStatusAndDelYn(UUID batchId, ContractStatus status, String delYn);

    // 회사별 전체 계약 목록
    List<Contract> findByCompanyIdAndDelYnOrderByCreatedAtDesc(UUID companyId, String delYn);

    // 계약 상세 (템플릿 fetch join)
    @Query("SELECT c FROM Contract c " +
            "JOIN FETCH c.contractTemplate " +
            "WHERE c.contractId = :contractId " +
            "AND c.delYn = 'N'")
    Optional<Contract> findByIdWithTemplate(@Param("contractId") UUID contractId);

    // 배치별 서명 완료 수
    @Query("SELECT COUNT(c) FROM Contract c " +
            "WHERE c.contractBatch.batchId = :batchId " +
            "AND c.contractStatus = :status " +
            "AND c.delYn = 'N'")
    long countByBatchIdAndStatus(@Param("batchId") UUID batchId, @Param("status") ContractStatus status);

    // 중복 SENT 차단용
    boolean existsByEmployeeMemberIdAndContractTemplateTemplateIdAndContractStatusAndDelYn(
            UUID employeeMemberId, UUID templateId, ContractStatus contractStatus, String delYn);

    // 상태 필터링 조회
    List<Contract> findByEmployeeMemberIdAndContractStatusAndDelYnOrderByCreatedAtDesc(
            UUID employeeMemberId, ContractStatus contractStatus, String delYn);

    @Query("SELECT COUNT(c) FROM Contract c " +
            "WHERE c.contractType = :contractType " +
            "  AND c.contractNumber IS NOT NULL " +
            "  AND FUNCTION('YEAR', c.createdAt) = :year " +
            "  AND c.delYn = 'N'")
    long countByContractTypeInYear(@Param("contractType") ContractType contractType,
                                   @Param("year") int year);
}
