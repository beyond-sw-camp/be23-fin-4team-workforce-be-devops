package com._team._team.approval.repository;

import com._team._team.approval.domain.ApprovalPolicyLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalPolicyLineRepository extends JpaRepository<ApprovalPolicyLine, UUID> {

    // 문서 양식별 활성 결재라인 정책 조회 (stepOrder 순)
    @Query("SELECT apl FROM ApprovalPolicyLine apl " +
            "WHERE apl.approvalDocument.documentId = :documentId " +
            "AND apl.delYn = 'N' " +
            "ORDER BY apl.stepOrder ASC")
    List<ApprovalPolicyLine> findByDocumentId(
            @Param("documentId") UUID documentId);
}