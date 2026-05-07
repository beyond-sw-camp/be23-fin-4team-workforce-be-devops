package com._team._team.approval.repository;

import com._team._team.approval.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    // 결재 요청별 첨부파일 목록
    @Query("SELECT a FROM Attachment a " +
            "WHERE a.request.requestId = :requestId")
    List<Attachment> findByRequestId(
            @Param("requestId") UUID requestId);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.request.requestId = :requestId")
    long countByRequestId(@Param("requestId") UUID requestId);


}
