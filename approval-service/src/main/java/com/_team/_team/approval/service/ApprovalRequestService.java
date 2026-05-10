package com._team._team.approval.service;

import com._team._team.approval.domain.*;
import com._team._team.approval.domain.enums.LineStatus;
import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.RequestType;
import com._team._team.approval.domain.enums.ViewerReadStatus;
import com._team._team.approval.dto.reqdto.ApprovalRequestCreateReqDto;
import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.dto.resdto.OfficialRecipientResDto;
import com._team._team.approval.feignclients.MemberServiceClient;
import com._team._team.approval.feignclients.dto.MemberPositionResDto;
import com._team._team.approval.feignclients.dto.OrganizationResDto;
import com._team._team.approval.publisher.AttendanceCorrectionApprovalEventPublisher;
import com._team._team.approval.publisher.AttendanceCorrectionSubmittedEventPublisher;
import com._team._team.approval.publisher.EarlyLeaveApprovalEventPublisher;
import com._team._team.approval.publisher.EarlyLeaveSubmittedEventPublisher;
import com._team._team.approval.repository.*;
import com._team._team.dto.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com._team._team.approval.domain.ApprovalSearchOutboxEvent;
import com._team._team.approval.repository.ApprovalSearchOutboxRepository;
import com._team._team.event.ApprovalChangedEvent;
import com._team._team.event.ApprovalDeletedEvent;
import com._team._team.event.ApprovalSavedEvent;
import com._team._team.event.AttendanceCorrectionSubmittedEvent;
import com._team._team.event.AttendanceCorrectionApprovalEvent;
import com._team._team.event.EarlyLeaveSubmittedEvent;
import com._team._team.event.EarlyLeaveApprovalEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final ApprovalRepository approvalRepository;
    private final ApprovalViewerRepository approvalViewerRepository;
    private final AttachmentService attachmentService;
    private final MemberServiceClient memberServiceClient;
    private final ApprovalNotificationService approvalNotificationService;
    private final OfficialRecipientRepository officialRecipientRepository;
    private final OfficialNumberService officialNumberService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApprovalSearchOutboxRepository approvalSearchOutboxRepository;
    private final ObjectMapper objectMapper;
    private final AttendanceCorrectionSubmittedEventPublisher attendanceCorrectionSubmittedEventPublisher;
    private final AttendanceCorrectionApprovalEventPublisher attendanceCorrectionApprovalEventPublisher;
    private final EarlyLeaveSubmittedEventPublisher earlyLeaveSubmittedEventPublisher;
    private final EarlyLeaveApprovalEventPublisher earlyLeaveApprovalEventPublisher;
    private final ApprovalPdfService approvalPdfService;


    @Autowired
    public ApprovalRequestService(ApprovalRequestRepository approvalRequestRepository, ApprovalDocumentRepository approvalDocumentRepository, ApprovalRepository approvalRepository, ApprovalViewerRepository approvalViewerRepository, AttachmentService attachmentService, MemberServiceClient memberServiceClient, ApprovalNotificationService approvalNotificationService, OfficialRecipientRepository officialRecipientRepository, OfficialNumberService officialNumberService, ApplicationEventPublisher eventPublisher, ApprovalSearchOutboxRepository approvalSearchOutboxRepository, ObjectMapper objectMapper, AttendanceCorrectionSubmittedEventPublisher attendanceCorrectionSubmittedEventPublisher, AttendanceCorrectionApprovalEventPublisher attendanceCorrectionApprovalEventPublisher, EarlyLeaveSubmittedEventPublisher earlyLeaveSubmittedEventPublisher, EarlyLeaveApprovalEventPublisher earlyLeaveApprovalEventPublisher, ApprovalPdfService approvalPdfService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.approvalRepository = approvalRepository;
        this.approvalViewerRepository = approvalViewerRepository;
        this.attachmentService = attachmentService;
        this.memberServiceClient = memberServiceClient;
        this.approvalNotificationService = approvalNotificationService;
        this.officialRecipientRepository = officialRecipientRepository;
        this.officialNumberService = officialNumberService;
        this.eventPublisher = eventPublisher;
        this.approvalSearchOutboxRepository = approvalSearchOutboxRepository;
        this.objectMapper = objectMapper;
        this.attendanceCorrectionSubmittedEventPublisher = attendanceCorrectionSubmittedEventPublisher;
        this.attendanceCorrectionApprovalEventPublisher = attendanceCorrectionApprovalEventPublisher;
        this.earlyLeaveSubmittedEventPublisher = earlyLeaveSubmittedEventPublisher;
        this.earlyLeaveApprovalEventPublisher = earlyLeaveApprovalEventPublisher;
        this.approvalPdfService = approvalPdfService;
    }

//    결재요청 생성(결재라인 + 참조/공람자 포함)
    public ApprovalRequestResDto create(UUID companyId, UUID memberId, UUID memberPositionId, ApprovalRequestCreateReqDto reqDto){

//        양식 조회 + 권한 체크
        ApprovalDocument document = approvalDocumentRepository.findById(reqDto.getDocumentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

//        요청 상태 검증 (DRAFT 또는 WAIT만 허용)
        if (reqDto.getRequestStatus() != RequestStatus.DRAFT && reqDto.getRequestStatus() != RequestStatus.WAIT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "요청 상태는 DRAFT 또는 WAIT만 가능합니다.");
        }

        // WAIT(제출)일 때 결재자 최소 1명 검증
        if (reqDto.getRequestStatus() == RequestStatus.WAIT) {
            if (reqDto.getApprovalLines() == null || reqDto.getApprovalLines().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "결재 제출 시 결재자는 최소 1명 이상이어야 합니다.");
            }
        }

        // === OFFICIAL 타입 검증: 수신 부서 최소 1개 ===
        if (document.getRequestType() == RequestType.OFFICIAL) {
            if (reqDto.getRecipients() == null || reqDto.getRecipients().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "공문은 수신 부서가 최소 1개 필요합니다.");
            }

            // 수신 부서 중복 검증
            long distinctRecipients = reqDto.getRecipients().stream()
                    .map(ApprovalRequestCreateReqDto.OfficialRecipientItem::getRecipientOrganizationId)
                    .distinct()
                    .count();
            if (distinctRecipients != reqDto.getRecipients().size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 수신 부서를 중복 지정할 수 없습니다.");
            }
        }

        // 결재자 중복 검증
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            List<UUID> memberPositionIds = reqDto.getApprovalLines().stream()
                    .map(ApprovalRequestCreateReqDto.ApprovalLineItem::getApproverMemberPositionId)
                    .toList();

            if (memberPositionIds.size() != memberPositionIds.stream().distinct().count()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 결재자를 중복 지정할 수 없습니다.");
            }
        }

        // 결재자에 본인 포함 불가
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            boolean selfInApprovers = reqDto.getApprovalLines().stream()
                    .anyMatch(line -> line.getApproverMemberId().equals(memberId));
            if (selfInApprovers) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "본인을 결재자로 지정할 수 없습니다.");
            }
        }

        // 참조/공람자 중복 검증
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            long distinctCount = reqDto.getViewers().stream()
                    .map(v -> v.getViewerMemberId().toString() + "_" + v.getViewerType().name())
                    .distinct()
                    .count();

            if (distinctCount != reqDto.getViewers().size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 참조/공람자를 중복 지정할 수 없습니다.");
            }
        }

        // 참조/공람자에 본인 포함 불가
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            boolean selfInViewers = reqDto.getViewers().stream()
                    .anyMatch(v -> v.getViewerMemberId().equals(memberId));
            if (selfInViewers) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "본인을 참조/공람자로 지정할 수 없습니다.");
            }
        }

        // stepOrder 연속성 검증
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            List<Integer> orders = reqDto.getApprovalLines().stream()
                    .map(ApprovalRequestCreateReqDto.ApprovalLineItem::getStepOrder)
                    .sorted()
                    .toList();

            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i) != i + 1) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "결재 순서(stepOrder)는 1부터 연속된 값이어야 합니다.");
                }
            }
        }

        // === 요청자 스냅샷 조회 ===
        // member-service에 요청자 정보 조회 (Feign 실패 시 트랜잭션 롤백)
        MemberPositionResDto requester;
        try {
            requester = memberServiceClient.getMemberPosition(memberPositionId);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인사 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        if (requester == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "요청자 정보를 찾을 수 없습니다.");
        }

        // ApprovalRequest 생성
        // 공문은 강제 공개
        String deptVisible = (document.getRequestType() == RequestType.OFFICIAL)
                ? "Y"
                : (reqDto.getIsDeptVisibleYn() != null ? reqDto.getIsDeptVisibleYn() : "Y");

        ApprovalRequest request = ApprovalRequest.builder()
                .approvalDocument(document)
                .memberId(memberId)
                .requestType(document.getRequestType())
                .contentJson(reqDto.getContentJson())
                .requestStatus(reqDto.getRequestStatus())
                .requesterName(requester.getMemberName())
                .requesterOrganizationId(requester.getOrganizationId())
                .requesterOrganizationName(requester.getOrganizationName())
                .isDeptVisibleYn(deptVisible)
                .formSchemaSnapshot(document.getFormSchema())
                .build();

        ApprovalRequest savedRequest = approvalRequestRepository.save(request);

//        Approval(결재라인) 생성
        List<Approval> savedApprovals = new ArrayList<>();
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()){
            List<Approval> approvals = reqDto.getApprovalLines().stream()
                    .map(line -> Approval.builder()
                            .request(savedRequest)
                            .approverMemberId(line.getApproverMemberId())
                            .approverMemberPositionId(line.getApproverMemberPositionId())
                            .approverName(line.getApproverName())
                            .stepOrder(line.getStepOrder())
                            .approvalStatus(reqDto.getRequestStatus() == RequestStatus.WAIT && line.getStepOrder() == 1
                                    ? LineStatus.PENDING
                                    : LineStatus.WAITING)
                            .build())
                    .toList();

            savedApprovals = approvalRepository.saveAll(approvals);
        }

        // ApprovalViewer(참조/공람자) 생성
        List<ApprovalViewer> savedViewers = new ArrayList<>();
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            List<ApprovalViewer> viewers = reqDto.getViewers().stream()
                    .map(viewer -> ApprovalViewer.builder()
                            .approvalRequest(savedRequest)
                            .viewerMemberId(viewer.getViewerMemberId())
                            .viewerMemberPositionId(viewer.getViewerMemberPositionId())
                            .viewerType(viewer.getViewerType())
                            .viewerReadStatus(ViewerReadStatus.UNREAD)
                            .build())
                    .toList();

            savedViewers = approvalViewerRepository.saveAll(viewers);
        }

        // === OFFICIAL 이면 수신 부서 저장 ===
        List<OfficialRecipient> savedRecipients = new ArrayList<>();
        if (document.getRequestType() == RequestType.OFFICIAL) {
            List<OfficialRecipient> recipients = reqDto.getRecipients().stream()
                    .map(item -> OfficialRecipient.builder()
                            .approvalRequest(savedRequest)
                            .recipientOrganizationId(item.getRecipientOrganizationId())
                            .recipientOrganizationName(item.getRecipientOrganizationName())
                            .build())
                    .toList();
            savedRecipients = officialRecipientRepository.saveAll(recipients);
        }

        // 알림 발행 - WAIT 제출 시에만
        if (savedRequest.getRequestStatus() == RequestStatus.WAIT) {

            // 문서 번호 생성
            String docNumber = officialNumberService.generate(document.getRequestType());
            savedRequest.updateDocumentNumber(docNumber);

            // 첫 결재자에게 알림
            Approval firstApproval = savedApprovals.stream()
                    .filter(a -> a.getStepOrder() == 1)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "첫 결재자를 찾을 수 없습니다."));
            approvalNotificationService.notifyApprovalRequested(savedRequest, firstApproval, savedViewers);

            // 근태정정신청 양식이면 일일근태 격리(UNDER_REVIEW)용 이벤트 발행
            publishAttendanceCorrectionSubmittedIfApplicable(document, savedRequest, memberId);
            // 조퇴계 양식이면 일일근태 격리(UNDER_REVIEW)용 이벤트 발행
            publishEarlyLeaveSubmittedIfApplicable(document, savedRequest, memberId);
        }

        // === [수정] fromEntity 호출 — recipients 인자 추가 ===
        List<OfficialRecipientResDto> recipientDtos = savedRecipients.stream()
                .map(OfficialRecipientResDto::fromEntity)
                .toList();

        // 검색 인덱싱 이벤트 발행
        eventPublisher.publishEvent(new ApprovalChangedEvent(savedRequest.getRequestId()));

        return ApprovalRequestResDto.fromEntity(savedRequest, savedApprovals, savedViewers, recipientDtos);


    }

    // 결재요청 상세 조회 (결재라인 + 참조/공람자 포함)
    @Transactional(readOnly = true)
    public ApprovalRequestResDto findById(UUID companyId, UUID memberId, UUID memberPositionId, UUID requestId) {
        // 결재요청 조회 (양식 정보 fetch join)
        ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 요청을 찾을 수 없습니다."));

        if (!request.getApprovalDocument().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // 결재라인 + 참조/공람자 조회
        List<Approval> approvals = approvalRepository.findByRequestIdWithRequest(requestId);
        List<ApprovalViewer> viewers = approvalViewerRepository.findByRequestId(requestId);

        // === [추가] OFFICIAL 접근 권한 체크 + recipients 로드 ===
        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL) {
            List<OfficialRecipient> recipients = officialRecipientRepository
                    .findByApprovalRequest_RequestId(requestId);

            // 현재 로그인 유저의 조직 ID 조회 (기존 Feign 재활용)
            MemberPositionResDto me;
            try {
                me = memberServiceClient.getMemberPosition(memberPositionId);
            } catch (Exception e) {
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "인사 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
            }
            UUID myOrgId = (me != null) ? me.getOrganizationId() : null;

            if (!canAccessOfficial(memberId, myOrgId, request, approvals, viewers, recipients)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "열람 권한이 없습니다.");
            }

            recipientDtos = recipients.stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }

        return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
    }

    private boolean canAccessOfficial(UUID memberId,
                                      UUID myOrgId,
                                      ApprovalRequest request,
                                      List<Approval> approvals,
                                      List<ApprovalViewer> viewers,
                                      List<OfficialRecipient> recipients) {

        // 1. 기안자
        if (request.getMemberId().equals(memberId)) return true;

        // 2. 결재자
        boolean isApprover = approvals.stream()
                .anyMatch(a -> a.getApproverMemberId().equals(memberId));
        if (isApprover) return true;

        // 3. 참조/공람자
        boolean isViewer = viewers.stream()
                .anyMatch(v -> v.getViewerMemberId().equals(memberId));
        if (isViewer) return true;

        // 4. 수신 부서 소속원
        if (myOrgId == null) return false;
        return recipients.stream()
                .anyMatch(r -> r.getRecipientOrganizationId().equals(myOrgId));
    }


    // 내가 올린 결재 목록
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findByMemberId(UUID memberId) {
        List<ApprovalRequest> requests =
                approvalRequestRepository.findByMemberIdAndDelYn(memberId, "N");

        return requests.stream()
                .map(request -> {
                    List<Approval> approvals =
                            approvalRepository.findByRequestIdWithRequest(request.getRequestId());
                    List<ApprovalViewer> viewers =
                            approvalViewerRepository.findByRequestId(request.getRequestId());

                    List<OfficialRecipientResDto> recipientDtos = null;
                    if (request.getRequestType() == RequestType.OFFICIAL) {
                        recipientDtos = officialRecipientRepository
                                .findByApprovalRequest_RequestId(request.getRequestId())
                                .stream()
                                .map(OfficialRecipientResDto::fromEntity)
                                .toList();
                    }
                    return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
                })
                .toList();
    }

    // 내가 올린 결재 목록 (상태별 필터)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findByMemberIdAndStatus(UUID memberId,
                                                               RequestStatus status) {
        List<ApprovalRequest> requests =
                approvalRequestRepository.findByMemberIdAndRequestStatusAndDelYn(
                        memberId, status, "N");

        return requests.stream()
                .map(request -> {
                    List<Approval> approvals =
                            approvalRepository.findByRequestIdWithRequest(request.getRequestId());
                    List<ApprovalViewer> viewers =
                            approvalViewerRepository.findByRequestId(request.getRequestId());

                    List<OfficialRecipientResDto> recipientDtos = null;
                    if (request.getRequestType() == RequestType.OFFICIAL) {
                        recipientDtos = officialRecipientRepository
                                .findByApprovalRequest_RequestId(request.getRequestId())
                                .stream()
                                .map(OfficialRecipientResDto::fromEntity)
                                .toList();
                    }
                    return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
                })
                .toList();
    }

//    임시저장 수정 (DRAFT 상태인 경우)

    public ApprovalRequestResDto update(UUID companyId, UUID memberId, UUID requestId, UUID memberPositionId, ApprovalRequestCreateReqDto reqDto){
//        결재요청 조회 및 검증
        ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 요청을 찾을 수 없습니다."));

        ApprovalDocument document = request.getApprovalDocument();

        if (!request.getApprovalDocument().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if (!request.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인의 결재 요청만 수정할 수 있습니다.");
        }

        if (request.getRequestStatus() != RequestStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "임시저장(DRAFT) 상태에서만 수정 가능합니다.");
        }

        // 상태 검증 (DRAFT 또는 WAIT만 허용)
        if (reqDto.getRequestStatus() != RequestStatus.DRAFT
                && reqDto.getRequestStatus() != RequestStatus.WAIT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "요청 상태는 DRAFT 또는 WAIT만 가능합니다.");
        }

        // 결재자에 본인 포함 불가
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            boolean selfInApprovers = reqDto.getApprovalLines().stream()
                    .anyMatch(line -> line.getApproverMemberId().equals(memberId));
            if (selfInApprovers) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "본인을 결재자로 지정할 수 없습니다.");
            }
        }

        // 참조/공람자에 본인 포함 불가
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            boolean selfInViewers = reqDto.getViewers().stream()
                    .anyMatch(v -> v.getViewerMemberId().equals(memberId));
            if (selfInViewers) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "본인을 참조/공람자로 지정할 수 없습니다.");
            }
        }

        // WAIT(제출)일 때 결재자 최소 1명 검증
        if (reqDto.getRequestStatus() == RequestStatus.WAIT) {
            if (reqDto.getApprovalLines() == null || reqDto.getApprovalLines().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "결재 제출 시 결재자는 최소 1명 이상이어야 합니다.");
            }
        }

        // 결재자 중복 검증
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            List<UUID> memberPositionIds = reqDto.getApprovalLines().stream()
                    .map(ApprovalRequestCreateReqDto.ApprovalLineItem::getApproverMemberPositionId)
                    .toList();

            if (memberPositionIds.size() != memberPositionIds.stream().distinct().count()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 결재자를 중복 지정할 수 없습니다.");
            }
        }

        // 참조/공람자 중복 검증
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            long distinctCount = reqDto.getViewers().stream()
                    .map(v -> v.getViewerMemberId().toString() + "_" + v.getViewerType().name())
                    .distinct()
                    .count();

            if (distinctCount != reqDto.getViewers().size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 참조/공람자를 중복 지정할 수 없습니다.");
            }
        }

        // === OFFICIAL 검증 ===
        if (document.getRequestType() == RequestType.OFFICIAL) {
            if (reqDto.getRecipients() == null || reqDto.getRecipients().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "공문은 수신 부서가 최소 1개 필요합니다.");
            }
            long distinctRecipients = reqDto.getRecipients().stream()
                    .map(ApprovalRequestCreateReqDto.OfficialRecipientItem::getRecipientOrganizationId)
                    .distinct()
                    .count();
            if (distinctRecipients != reqDto.getRecipients().size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "동일한 수신 부서를 중복 지정할 수 없습니다.");
            }
        }

        // stepOrder 연속성 검증
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            List<Integer> orders = reqDto.getApprovalLines().stream()
                    .map(ApprovalRequestCreateReqDto.ApprovalLineItem::getStepOrder)
                    .sorted()
                    .toList();

            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i) != i + 1) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "결재 순서(stepOrder)는 1부터 연속된 값이어야 합니다.");
                }
            }
        }

        // 결재요청 내용 수정
        request.updateContentJson(reqDto.getContentJson());
        request.updateStatus(reqDto.getRequestStatus());

        // 기존 결재라인 물리 삭제 후 새로 저장
        List<Approval> existingApprovals =
                approvalRepository.findByRequestIdWithRequest(requestId);
        approvalRepository.deleteAll(existingApprovals);

        List<Approval> savedApprovals = new ArrayList<>();
        if (reqDto.getApprovalLines() != null && !reqDto.getApprovalLines().isEmpty()) {
            List<Approval> newApprovals = reqDto.getApprovalLines().stream()
                    .map(line -> Approval.builder()
                            .request(request)
                            .approverMemberId(line.getApproverMemberId())
                            .approverMemberPositionId(line.getApproverMemberPositionId())
                            .approverName(line.getApproverName())
                            .stepOrder(line.getStepOrder())
                            .approvalStatus(reqDto.getRequestStatus() == RequestStatus.WAIT && line.getStepOrder() == 1
                                    ? LineStatus.PENDING
                                    : LineStatus.WAITING)
                            .build())
                    .toList();

            savedApprovals = approvalRepository.saveAll(newApprovals);
        }

        // 기존 참조/공람자 물리 삭제 후 새로 저장
        List<ApprovalViewer> existingViewers = approvalViewerRepository.findByRequestId(requestId);
        approvalViewerRepository.deleteAll(existingViewers);

        // 기존 첨부파일 전체 삭제 (S3 + DB)
        attachmentService.deleteAllByRequestId(requestId);

        // === OFFICIAL 이면 수신 부서 전체 교체 ===
        List<OfficialRecipient> savedRecipients = new ArrayList<>();
        if (document.getRequestType() == RequestType.OFFICIAL) {
            officialRecipientRepository.deleteByApprovalRequest_RequestId(requestId);

            List<OfficialRecipient> newRecipients = reqDto.getRecipients().stream()
                    .map(item -> OfficialRecipient.builder()
                            .approvalRequest(request)
                            .recipientOrganizationId(item.getRecipientOrganizationId())
                            .recipientOrganizationName(item.getRecipientOrganizationName())
                            .build())
                    .toList();
            savedRecipients = officialRecipientRepository.saveAll(newRecipients);
        }

        List<ApprovalViewer> savedViewers = new ArrayList<>();
        if (reqDto.getViewers() != null && !reqDto.getViewers().isEmpty()) {
            List<ApprovalViewer> newViewers = reqDto.getViewers().stream()
                    .map(viewer -> ApprovalViewer.builder()
                            .approvalRequest(request)
                            .viewerMemberId(viewer.getViewerMemberId())
                            .viewerMemberPositionId(viewer.getViewerMemberPositionId())
                            .viewerType(viewer.getViewerType())
                            .viewerReadStatus(ViewerReadStatus.UNREAD)
                            .build())
                    .toList();

            savedViewers = approvalViewerRepository.saveAll(newViewers);
        }

        List<OfficialRecipientResDto> recipientDtos = savedRecipients.stream()
                .map(OfficialRecipientResDto::fromEntity)
                .toList();
        return ApprovalRequestResDto.fromEntity(request, savedApprovals, savedViewers, recipientDtos);

    }

//    결재 취소
    public ApprovalRequestResDto cancel(UUID companyId, UUID memberId, UUID requestId, String cancelReason){

//        결재요청 조회 + 검증
        ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 요청을 찾을 수 없습니다."));

        RequestStatus prevStatus = request.getRequestStatus();

        if (!request.getApprovalDocument().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if (!request.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 결재 요청만 취소할 수 있습니다.");
        }

        // DRAFT, WAIT만 취소 가능
        boolean isDraftOrWait = request.getRequestStatus() == RequestStatus.DRAFT
                || request.getRequestStatus() == RequestStatus.WAIT;

        boolean isApprovedOfficialNotSent = request.getRequestStatus() == RequestStatus.APPROVED
                && request.getRequestType() == RequestType.OFFICIAL
                && "N".equals(request.getSendYn());

        if (!isDraftOrWait && !isApprovedOfficialNotSent) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "취소할 수 없는 상태입니다.");
        }

        // 취소사유 필수 검증
        if (cancelReason == null || cancelReason.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "취소 사유는 필수입니다.");
        }

        // 결재요청 취소 처리
        request.cancel(cancelReason);

        // 결재라인도 전부 CANCELED 처리
        List<Approval> approvals =
                approvalRepository.findByRequestIdWithRequest(requestId);
        approvals.forEach(Approval::cancel);

        List<ApprovalViewer> viewers =
                approvalViewerRepository.findByRequestId(requestId);

        // 알림 발행 - WAIT 상태에서 취소된 경우만 (DRAFT 는 알림 미발행)
        if (prevStatus == RequestStatus.WAIT) {
            // 현재 PENDING 또는 첫 단계 결재자에게 알림
            Approval activeApproval = approvals.stream()
                    .filter(a -> a.getApprovalStatus() == LineStatus.PENDING)
                    .findFirst()
                    .orElseGet(() -> approvals.stream()
                            .filter(a -> a.getStepOrder() == 1)
                            .findFirst()
                            .orElse(null));
            approvalNotificationService.notifyCanceled(request, activeApproval, viewers);
        }

        // 승인 완료된 공문 미발송 취소 시 → 결재자 + 참조자에게 알림
        if (prevStatus == RequestStatus.APPROVED) {
            approvalNotificationService.notifyOfficialCanceled(request, approvals, viewers);
        }

        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL) {
            recipientDtos = officialRecipientRepository
                    .findByApprovalRequest_RequestId(requestId)
                    .stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }

        // ES 삭제 이벤트 직접 저장
        saveApprovalSearchOutboxDeleteEvent(request.getRequestId());

        // 정정/조퇴 결재가 취소되면 격리(UNDER_REVIEW) 해제 위해 CANCEL 이벤트 발행
        publishAttendanceCorrectionCancelIfApplicable(request, memberId, cancelReason);
        publishEarlyLeaveCancelIfApplicable(request, memberId, cancelReason);

        return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
    }

    // 부서 문서함 조회 (본인 부서 + 하위 조직의 결재 요청, 작성 시점 부서 기준)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findDepartmentRequests(
            UUID companyId, UUID memberPositionId, UUID memberId, UUID organizationId, RequestType requestType) {

        // 1. 요청자 본인 정보 조회
        MemberPositionResDto requester;
        try {
            requester = memberServiceClient.getMemberPosition(memberPositionId);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인사 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        if (requester == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }

        // 2. 요청된 organizationId가 본인 소속 부서와 일치하는지 검증
        if (!requester.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인이 속한 부서의 문서함만 조회할 수 있습니다.");
        }

        List<ApprovalRequest> requests = (requestType == null)
                ? approvalRequestRepository.findDepartmentRequests(companyId, organizationId)
                : approvalRequestRepository.findDepartmentRequestsByType(companyId, organizationId, requestType);

        return requests.stream()
                .map(request -> {
                    // 받은 공문 = 공문 타입 + 작성자 부서가 내 부서가 아님
                    boolean isReceivedOfficial =
                            request.getRequestType() == RequestType.OFFICIAL
                                    && !organizationId.equals(request.getRequesterOrganizationId());
                    // 비공개 문서 + 작성자 본인이 아닌 경우 → 마스킹
                    if ("N".equals(request.getIsDeptVisibleYn())
                            && !request.getMemberId().equals(memberId) && !isReceivedOfficial) {
                        return ApprovalRequestResDto.maskedFromEntity(request);
                    }
                    return toResDtoWithRecipients(request);
                })
                .toList();
    }

    // 공통 헬퍼 — recipients 자동 포함
    private ApprovalRequestResDto toResDtoWithRecipients(ApprovalRequest request) {
        List<Approval> approvals = approvalRepository.findByRequestIdWithRequest(request.getRequestId());
        List<ApprovalViewer> viewers = approvalViewerRepository.findByRequestId(request.getRequestId());

        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL) {
            recipientDtos = officialRecipientRepository
                    .findByApprovalRequest_RequestId(request.getRequestId())
                    .stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }
        return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findReceivedOfficials(
            UUID companyId, UUID memberPositionId) {

        MemberPositionResDto me;
        try {
            me = memberServiceClient.getMemberPosition(memberPositionId);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인사 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        if (me == null || me.getOrganizationId() == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }

        // 내 조직 + 상위 조직 계열의 ID 목록 수집
        List<UUID> orgIds = collectAncestorOrgIds(me.getOrganizationId());

        // 이 조직들 중 하나라도 수신 부서인 공문 requestId 수집
        List<UUID> requestIds = officialRecipientRepository
                .findRequestIdsByRecipientOrganizationIds(orgIds);

        if (requestIds.isEmpty()) {
            return List.of();
        }

        List<ApprovalRequest> requests = approvalRequestRepository
                .findApprovedOfficialsByIdsAndCompany(requestIds, companyId);

        return requests.stream()
                .map(this::toResDtoWithRecipients)
                .toList();
    }

    // 내 조직부터 루트까지 상위 조직 ID 수집
    private List<UUID> collectAncestorOrgIds(UUID startOrgId) {
        List<UUID> orgIds = new ArrayList<>();
        UUID currentId = startOrgId;
        int maxDepth = 10;

        for (int i = 0; i < maxDepth && currentId != null; i++) {
            orgIds.add(currentId);
            try {
                OrganizationResDto org = memberServiceClient.getOrganization(currentId);
                if (org == null || org.getParentId() == null) break;
                currentId = org.getParentId();
            } catch (Exception e) {
                break; // 조직 조회 실패 시 현재까지 수집된 목록으로 진행
            }
        }

        return orgIds;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findMyRequests(
            UUID memberId, RequestStatus status, RequestType requestType) {

        List<ApprovalRequest> requests = approvalRequestRepository
                .findMyRequests(memberId, status, requestType);

        return requests.stream()
                .map(this::toResDtoWithRecipients)
                .toList();
    }

//    공문 발송 (기안자 수동 발송)
@Transactional
public ApprovalRequestResDto sendOfficial(UUID companyId, UUID memberId, UUID memberPositionId, UUID requestId) {

    ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "결재 요청을 찾을 수 없습니다."));

    // 회사 검증
    if (!request.getApprovalDocument().getCompanyId().equals(companyId)) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 기안자 본인 검증
    if (!request.getMemberId().equals(memberId)) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "기안자만 공문을 발송할 수 있습니다.");
    }

    // OFFICIAL 타입 검증
    if (request.getRequestType() != RequestType.OFFICIAL) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "공문 타입의 결재만 발송할 수 있습니다.");
    }

    // 승인 상태 검증
    if (request.getRequestStatus() != RequestStatus.APPROVED) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "승인된 결재만 발송할 수 있습니다.");
    }

    // 중복 발송 방지
    if ("Y".equals(request.getSendYn())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 발송된 공문입니다.");
    }

    // 수신 부서 존재 여부 검증
    List<OfficialRecipient> recipients = officialRecipientRepository
            .findByApprovalRequest_RequestId(requestId);
    if (recipients.isEmpty()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "수신 부서가 없습니다.");
    }

    // 발송 처리
    request.send();

    // 공문 발송 이벤트 발행 (Kafka)
    approvalNotificationService.publishOfficialApprovedEvent(request);

    List<Approval> approvals = approvalRepository.findByRequestIdWithRequest(requestId);
    List<ApprovalViewer> viewers = approvalViewerRepository.findByRequestId(requestId);

    List<OfficialRecipientResDto> recipientDtos = recipients.stream()
            .map(OfficialRecipientResDto::fromEntity)
            .toList();

    return ApprovalRequestResDto.fromEntity(request, approvals, viewers, recipientDtos);
}

    // 근태정정신청 양식 결재 상신 시 일일근태 격리(UNDER_REVIEW) 이벤트 발행
    // 출/퇴근시각은 LocalDateTime 생성
    private void publishAttendanceCorrectionSubmittedIfApplicable(ApprovalDocument document,
                                                                  ApprovalRequest request,
                                                                  UUID memberId) {
        if (!"근태정정신청".equals(document.getDocumentName())) return;

        JsonNode node = readContentSafely(request.getContentJson());
        if (node == null) return;

        LocalDate date = parseDateOrNull(node, "attendanceDate");
        if (date == null) {
            log.warn("[AttendanceCorrectionSubmit] 정정 일자 누락, requestId={}", request.getRequestId());
            return;
        }

        try {
            attendanceCorrectionSubmittedEventPublisher.publish(
                    AttendanceCorrectionSubmittedEvent.builder()
                            .companyId(document.getCompanyId())
                            .memberId(memberId)
                            .requestId(request.getRequestId())
                            .attendanceDate(date)
                            .requestedClockIn(combineLocalDateTime(date, parseTextOrNull(node, "requestedClockIn")))
                            .requestedClockOut(combineLocalDateTime(date, parseTextOrNull(node, "requestedClockOut")))
                            .reason(parseTextOrNull(node, "reason"))
                            .submittedBy(memberId)
                            .submittedAt(LocalDateTime.now())
                            .build());
        } catch (Exception e) {
            log.error("[AttendanceCorrectionSubmit] publish 실패 - 결재 상신은 진행, requestId={}",
                    request.getRequestId(), e);
        }
    }

    // 조퇴계 양식 결재 상신 시 일일근태 격리(UNDER_REVIEW) 이벤트 발행
    private void publishEarlyLeaveSubmittedIfApplicable(ApprovalDocument document,
                                                        ApprovalRequest request,
                                                        UUID memberId) {
        if (!"조퇴계".equals(document.getDocumentName())) return;

        JsonNode node = readContentSafely(request.getContentJson());
        if (node == null) return;

        LocalDate date = parseDateOrNull(node, "attendanceDate");
        if (date == null) {
            log.warn("[EarlyLeaveSubmit] 조퇴 일자 누락, requestId={}", request.getRequestId());
            return;
        }

        try {
            earlyLeaveSubmittedEventPublisher.publish(
                    EarlyLeaveSubmittedEvent.builder()
                            .companyId(document.getCompanyId())
                            .memberId(memberId)
                            .requestId(request.getRequestId())
                            .attendanceDate(date)
                            .earlyLeaveAt(combineLocalDateTime(date, parseTextOrNull(node, "earlyLeaveTime")))
                            .reason(parseTextOrNull(node, "reason"))
                            .submittedBy(memberId)
                            .submittedAt(LocalDateTime.now())
                            .build());
        } catch (Exception e) {
            log.error("[EarlyLeaveSubmit] publish 실패 - 결재 상신은 진행, requestId={}",
                    request.getRequestId(), e);
        }
    }

    // 근태정정신청 결재 취소 시 격리 해제용 CANCEL 이벤트 발행
    private void publishAttendanceCorrectionCancelIfApplicable(ApprovalRequest request,
                                                                UUID approverId,
                                                                String cancelReason) {
        ApprovalDocument document = request.getApprovalDocument();
        if (!"근태정정신청".equals(document.getDocumentName())) return;

        JsonNode node = readContentSafely(request.getContentJson());
        if (node == null) return;
        LocalDate date = parseDateOrNull(node, "attendanceDate");
        if (date == null) return;

        try {
            attendanceCorrectionApprovalEventPublisher.publish(
                    AttendanceCorrectionApprovalEvent.builder()
                            .companyId(document.getCompanyId())
                            .memberId(request.getMemberId())
                            .requestId(request.getRequestId())
                            .attendanceDate(date)
                            .requestedClockIn(combineLocalDateTime(date, parseTextOrNull(node, "requestedClockIn")))
                            .requestedClockOut(combineLocalDateTime(date, parseTextOrNull(node, "requestedClockOut")))
                            .reason(parseTextOrNull(node, "reason"))
                            .approverId(approverId)
                            .decidedAt(LocalDateTime.now())
                            .note(cancelReason)
                            .action(AttendanceCorrectionApprovalEvent.Action.CANCEL)
                            .build());
        } catch (Exception e) {
            log.error("[AttendanceCorrection] cancel publish 실패. requestId={}", request.getRequestId(), e);
        }
    }

    // 조퇴계 결재 취소 시 격리 해제용 CANCEL 이벤트 발행
    private void publishEarlyLeaveCancelIfApplicable(ApprovalRequest request,
                                                      UUID approverId,
                                                      String cancelReason) {
        ApprovalDocument document = request.getApprovalDocument();
        if (!"조퇴계".equals(document.getDocumentName())) return;

        JsonNode node = readContentSafely(request.getContentJson());
        if (node == null) return;
        LocalDate date = parseDateOrNull(node, "attendanceDate");
        if (date == null) return;

        try {
            earlyLeaveApprovalEventPublisher.publish(
                    EarlyLeaveApprovalEvent.builder()
                            .companyId(document.getCompanyId())
                            .memberId(request.getMemberId())
                            .requestId(request.getRequestId())
                            .attendanceDate(date)
                            .earlyLeaveAt(combineLocalDateTime(date, parseTextOrNull(node, "earlyLeaveTime")))
                            .reason(parseTextOrNull(node, "reason"))
                            .approverId(approverId)
                            .decidedAt(LocalDateTime.now())
                            .note(cancelReason)
                            .action(EarlyLeaveApprovalEvent.Action.CANCEL)
                            .build());
        } catch (Exception e) {
            log.error("[EarlyLeave] cancel publish 실패. requestId={}", request.getRequestId(), e);
        }
    }

    private JsonNode readContentSafely(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String parseTextOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    private LocalDate parseDateOrNull(JsonNode node, String field) {
        String text = parseTextOrNull(node, field);
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime combineLocalDateTime(LocalDate date, String time) {
        if (date == null || time == null || time.isBlank()) return null;
        try {
            return LocalDateTime.of(date, LocalTime.parse(time));
        } catch (Exception e) {
            return null;
        }
    }

    // ======== 검색 인덱싱 이벤트 처리 (Outbox 패턴) ========

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleApprovalChangedEvent(ApprovalChangedEvent event) {
        saveApprovalSearchOutboxEvent(event.getRequestId());
    }

    public void saveApprovalSearchOutboxEvent(UUID requestId) {
        try {
            ApprovalRequest request = approvalRequestRepository
                    .findByIdWithDocument(requestId)
                    .orElse(null);

            if (request == null) {
                log.warn("[APPROVAL-OUTBOX] request not found. requestId={}", requestId);
                return;
            }

            ApprovalSavedEvent savedEvent = ApprovalSavedEvent.builder()
                    .requestId(request.getRequestId())
                    .companyId(request.getApprovalDocument().getCompanyId())
                    .memberId(request.getMemberId())
                    .requesterName(request.getRequesterName())
                    .requesterOrganizationName(request.getRequesterOrganizationName())
                    .requesterOrganizationId(request.getRequesterOrganizationId())
                    .documentName(request.getApprovalDocument().getDocumentName())
                    .requestStatus(request.getRequestStatus().name())
                    .requestType(request.getRequestType().name())
                    .contentJson(request.getContentJson())
                    .createdAt(request.getCreatedAt())
                    .isDeptVisibleYn(request.getIsDeptVisibleYn())
                    .build();

            String payload = objectMapper.writeValueAsString(savedEvent);

            ApprovalSearchOutboxEvent outboxEvent = ApprovalSearchOutboxEvent.builder()
                    .topic("approval-saved")
                    .aggregateId(request.getRequestId())
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            approvalSearchOutboxRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("ApprovalSearchOutboxEvent 저장 실패: {}", e.getMessage());
        }
    }

    private void saveApprovalSearchOutboxDeleteEvent(UUID requestId) {
        try {
            ApprovalDeletedEvent event = ApprovalDeletedEvent.builder()
                    .requestId(requestId)
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            ApprovalSearchOutboxEvent outboxEvent = ApprovalSearchOutboxEvent.builder()
                    .topic("approval-deleted")
                    .aggregateId(requestId)
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            approvalSearchOutboxRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("ApprovalSearchOutboxDeleteEvent 저장 실패: {}", e.getMessage());
        }
    }

    // 결재 문서 PDF 다운로드
    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID companyId, UUID memberId, UUID requestId) {
        ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
                .orElseThrow(() -> new EntityNotFoundException("결재 문서를 찾을 수 없습니다."));

        // 임시저장 문서는 다운로드 불가
        if (request.getRequestStatus() == RequestStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "임시저장 문서는 다운로드할 수 없습니다.");
        }

        List<Approval> approvalLines = approvalRepository.findByRequestIdWithRequest(requestId);

        boolean isRequester = request.getMemberId().equals(memberId);
        boolean isApprover = approvalLines.stream()
                .anyMatch(a -> a.getApproverMemberId().equals(memberId));

        if (!isRequester && !isApprover) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return approvalPdfService.buildPdf(request, approvalLines);
    }
}
