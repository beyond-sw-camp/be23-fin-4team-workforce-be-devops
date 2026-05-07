package com._team._team.approval.repository;

import com._team._team.approval.domain.OfficialRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OfficialRecipientRepository extends JpaRepository<OfficialRecipient, UUID> {

    List<OfficialRecipient> findByApprovalRequest_RequestId(UUID requestId);

    void deleteByApprovalRequest_RequestId(UUID requestId);

    // 특정 부서가 수신한 공문 requestId 목록
    @Query("SELECT o.approvalRequest.requestId FROM OfficialRecipient o " +
            "WHERE o.recipientOrganizationId = :organizationId")
    List<UUID> findRequestIdsByRecipientOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("SELECT DISTINCT r.approvalRequest.requestId FROM OfficialRecipient r " +
            "WHERE r.recipientOrganizationId IN :organizationIds")
    List<UUID> findRequestIdsByRecipientOrganizationIds(
            @Param("organizationIds") List<UUID> organizationIds);


}
