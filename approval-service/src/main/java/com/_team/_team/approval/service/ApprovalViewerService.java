package com._team._team.approval.service;

import com._team._team.approval.domain.Approval;
import com._team._team.approval.domain.ApprovalRequest;
import com._team._team.approval.domain.ApprovalViewer;
import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.ViewerReadStatus;
import com._team._team.approval.domain.enums.ViewerType;
import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.repository.ApprovalRepository;
import com._team._team.approval.repository.ApprovalViewerRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApprovalViewerService {

    private final ApprovalViewerRepository approvalViewerRepository;
    private final ApprovalRepository approvalRepository;

    @Autowired
    public ApprovalViewerService(ApprovalViewerRepository approvalViewerRepository, ApprovalRepository approvalRepository) {
        this.approvalViewerRepository = approvalViewerRepository;
        this.approvalRepository = approvalRepository;
    }

    // 내가 참조(CC)로 지정된 결재 목록
    public List<ApprovalRequestResDto> findCcList(UUID memberId) {

        List<ApprovalViewer> viewers = approvalViewerRepository.findByViewerMemberIdAndViewerType(
                memberId, ViewerType.CC);

        List<ApprovalRequestResDto> result = new ArrayList<>();
        for (ApprovalViewer viewer : viewers) {
            ApprovalRequest request = viewer.getApprovalRequest();


            if (request.getRequestStatus() == RequestStatus.CANCELED) {
                continue;
            }

            if (request.getRequestStatus() == RequestStatus.DRAFT) {
                continue;
            }

            List<Approval> approvals = approvalRepository
                    .findByRequestIdWithRequest(request.getRequestId());
            List<ApprovalViewer> allViewers = approvalViewerRepository
                    .findByRequestId(request.getRequestId());

            result.add(ApprovalRequestResDto.fromEntity(request, approvals, allViewers, null));
        }
        return result;
    }

    // 내가 공람(CIRCULATION)으로 지정된 결재 목록 (최종 승인된 건만)
    public List<ApprovalRequestResDto> findCirculationList(UUID memberId) {

        List<ApprovalViewer> viewers = approvalViewerRepository.findByViewerMemberIdAndViewerType(
                memberId, ViewerType.CIRCULATION);

        List<ApprovalRequestResDto> result = new ArrayList<>();
        for (ApprovalViewer viewer : viewers) {
            ApprovalRequest request = viewer.getApprovalRequest();

            // 공람자는 최종 승인된 건만 조회 가능
            if (request.getRequestStatus() != RequestStatus.APPROVED) {
                continue;
            }

            List<Approval> approvals = approvalRepository
                    .findByRequestIdWithRequest(request.getRequestId());
            List<ApprovalViewer> allViewers = approvalViewerRepository
                    .findByRequestId(request.getRequestId());

            result.add(ApprovalRequestResDto.fromEntity(request, approvals, allViewers, null));
        }
        return result;
    }

    // 읽음 처리
    @Transactional
    public void markAsRead(UUID memberId, UUID viewerId) {

        ApprovalViewer viewer = approvalViewerRepository.findById(viewerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "참조/공람 정보를 찾을 수 없습니다."));

        if (!viewer.getViewerMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 참조/공람건만 읽음 처리할 수 있습니다.");
        }

        ApprovalRequest request = viewer.getApprovalRequest();
        if (request.getRequestStatus() == RequestStatus.CANCELED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "취소된 결재건은 읽음 처리할 수 없습니다.");
        }

        if (viewer.getViewerReadStatus() == ViewerReadStatus.READ) {
            return;
        }

        viewer.markAsRead();
    }
}
