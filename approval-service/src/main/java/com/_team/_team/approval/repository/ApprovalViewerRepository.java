package com._team._team.approval.repository;

import com._team._team.approval.domain.ApprovalViewer;
import com._team._team.approval.domain.enums.ViewerType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalViewerRepository extends JpaRepository<ApprovalViewer, UUID> {

    // 결재 요청별 참조/공람자 목록
    @Query("SELECT av FROM ApprovalViewer av " +
            "WHERE av.approvalRequest.requestId = :requestId")
    List<ApprovalViewer> findByRequestId(
            @Param("requestId") UUID requestId);

    // 결재 요청별 + 타입별 조회 (참조만 / 공람만)
    @Query("SELECT av FROM ApprovalViewer av " +
            "WHERE av.approvalRequest.requestId = :requestId " +
            "AND av.viewerType = :viewerType")
    List<ApprovalViewer> findByRequestIdAndViewerType(
            @Param("requestId") UUID requestId,
            @Param("viewerType") ViewerType viewerType);

    // 내가 참조/공람으로 지정된 목록
    @Query("SELECT av FROM ApprovalViewer av " +
            "JOIN FETCH av.approvalRequest r " +
            "JOIN FETCH r.approvalDocument " +
            "WHERE av.viewerMemberId = :memberId " +
            "AND r.delYn = 'N'")
    List<ApprovalViewer> findByViewerMemberId(
            @Param("memberId") UUID memberId);

    @Query("SELECT av FROM ApprovalViewer av " +
            "JOIN FETCH av.approvalRequest r " +
            "JOIN FETCH r.approvalDocument " +
            "WHERE av.viewerMemberId = :memberId " +
            "AND av.viewerType = :viewerType " +
            "AND r.delYn = 'N'")
    List<ApprovalViewer> findByViewerMemberIdAndViewerType(
            @Param("memberId") UUID memberId,
            @Param("viewerType") ViewerType viewerType);
}


