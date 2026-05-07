package com._team._team.approval.repository;

import com._team._team.approval.domain.ApprovalDocument;
import com._team._team.approval.domain.enums.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalDocumentRepository extends JpaRepository<ApprovalDocument, UUID> {

    // 회사별 활성 문서 양식 목록
    List<ApprovalDocument> findByCompanyIdAndIsActiveYn(UUID companyId, String isActiveYn);

    // 회사별 + 요청타입으로 문서 양식 조회
    List<ApprovalDocument> findByCompanyIdAndRequestTypeAndIsActiveYn(
            UUID companyId, RequestType requestType, String isActiveYn);

    // 회사별 전체 문서 양식 목록
    List<ApprovalDocument> findByCompanyId(UUID companyId);

    // 같은 회사 내 문서 이름 중복 체크 (활성/비활성 무관)
    boolean existsByCompanyIdAndDocumentName(UUID companyId, String documentName);

    // 같은 회사 내 문서 이름 중복 체크 (자기 자신 제외, 수정 시 사용)
    boolean existsByCompanyIdAndDocumentNameAndDocumentIdNot(
            UUID companyId, String documentName, UUID documentId);

}
