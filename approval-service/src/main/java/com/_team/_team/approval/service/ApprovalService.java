package com._team._team.approval.service;

import com._team._team.approval.domain.*;
import com._team._team.approval.domain.enums.LineStatus;
import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.RequestType;
import com._team._team.approval.dto.reqdto.ApprovalActionReqDto;
import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.dto.resdto.OfficialRecipientResDto;
import com._team._team.approval.feignclients.MemberServiceClient;
import com._team._team.approval.feignclients.dto.SignatureResDto;
import com._team._team.approval.publisher.*;
import com._team._team.approval.repository.AbsenceProxyRepository;
import com._team._team.approval.repository.ApprovalRepository;
import com._team._team.approval.repository.ApprovalViewerRepository;
import com._team._team.approval.repository.OfficialRecipientRepository;
import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.event.*;
import com._team._team.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@Slf4j
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final ApprovalViewerRepository approvalViewerRepository;
    private final AbsenceProxyRepository absenceProxyRepository;
    private final ApprovalNotificationService approvalNotificationService;
    private final OvertimeApprovalEventPublisher overtimeApprovalEventPublisher;
    private final LeaveApprovalEventPublisher leaveApprovalEventPublisher;
    private final AllowanceApprovalEventPublisher allowanceApprovalEventPublisher;
    private final ScheduleApprovalEventPublisher scheduleApprovalEventPublisher;
    private final ObjectMapper objectMapper;
    private final OfficialRecipientRepository officialRecipientRepository;
    private final MemberServiceClient memberServiceClient;
    private final LeaveOfAbsenceApprovalEventPublisher.CalendarApprovalEventPublisher calendarApprovalEventPublisher;
    private final ResignationApprovalEventPublisher resignationApprovalEventPublisher;
    private final AttendanceCorrectionApprovalEventPublisher attendanceCorrectionApprovalEventPublisher;
    private final EarlyLeaveApprovalEventPublisher earlyLeaveApprovalEventPublisher;

    private final ApplicationEventPublisher eventPublisher;
    private final LeaveOfAbsenceApprovalEventPublisher leaveOfAbsenceApprovalEventPublisher;
    private final PersonnelOrderApprovalEventPublisher personnelOrderApprovalEventPublisher;

    @Autowired
    public ApprovalService(ApprovalRepository approvalRepository,
                           ApprovalViewerRepository approvalViewerRepository,
                           AbsenceProxyRepository absenceProxyRepository,
                           ApprovalNotificationService approvalNotificationService,
                           OvertimeApprovalEventPublisher overtimeApprovalEventPublisher,
                           LeaveApprovalEventPublisher leaveApprovalEventPublisher,
                           AllowanceApprovalEventPublisher allowanceApprovalEventPublisher,
                           ScheduleApprovalEventPublisher scheduleApprovalEventPublisher,
                           ObjectMapper objectMapper,
                           OfficialRecipientRepository officialRecipientRepository,
                           MemberServiceClient memberServiceClient,
                           LeaveOfAbsenceApprovalEventPublisher.CalendarApprovalEventPublisher calendarApprovalEventPublisher, ResignationApprovalEventPublisher resignationApprovalEventPublisher,
                           AttendanceCorrectionApprovalEventPublisher attendanceCorrectionApprovalEventPublisher,
                           EarlyLeaveApprovalEventPublisher earlyLeaveApprovalEventPublisher,
                           ApplicationEventPublisher eventPublisher,
                           LeaveOfAbsenceApprovalEventPublisher leaveOfAbsenceApprovalEventPublisher,
                           PersonnelOrderApprovalEventPublisher personnelOrderApprovalEventPublisher) {
        this.approvalRepository = approvalRepository;
        this.approvalViewerRepository = approvalViewerRepository;
        this.absenceProxyRepository = absenceProxyRepository;
        this.approvalNotificationService = approvalNotificationService;
        this.overtimeApprovalEventPublisher = overtimeApprovalEventPublisher;
        this.leaveApprovalEventPublisher = leaveApprovalEventPublisher;
        this.allowanceApprovalEventPublisher = allowanceApprovalEventPublisher;
        this.scheduleApprovalEventPublisher = scheduleApprovalEventPublisher;
        this.objectMapper = objectMapper;
        this.officialRecipientRepository = officialRecipientRepository;
        this.memberServiceClient = memberServiceClient;
        this.calendarApprovalEventPublisher = calendarApprovalEventPublisher;
        this.resignationApprovalEventPublisher = resignationApprovalEventPublisher;
        this.attendanceCorrectionApprovalEventPublisher = attendanceCorrectionApprovalEventPublisher;
        this.earlyLeaveApprovalEventPublisher = earlyLeaveApprovalEventPublisher;
        this.eventPublisher = eventPublisher;
        this.leaveOfAbsenceApprovalEventPublisher = leaveOfAbsenceApprovalEventPublisher;
        this.personnelOrderApprovalEventPublisher = personnelOrderApprovalEventPublisher;
    }


    // 결재 완료함 (내가 결재한 문서 목록)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findActed(UUID memberId, UUID memberPositionId) {

        List<LineStatus> actedStatuses = List.of(LineStatus.APPROVED, LineStatus.REJECTED);

        // 1) 내가 직접 처리한 건 (기존)
        List<Approval> myActed =
                approvalRepository.findActedByMemberPositionId(memberPositionId, actedStatuses);

        // 2) 내가 대결자로서 처리한 건
        List<Approval> proxyActed =
                approvalRepository.findActedByActualApproverMemberId(memberId, actedStatuses);

        // 3) 합산 (중복 제거 — requestId 기준, actedAt 최신 순 유지)
        Map<UUID, Approval> mergedMap = new LinkedHashMap<>();
        for (Approval a : myActed) {
            mergedMap.putIfAbsent(a.getRequest().getRequestId(), a);
        }
        for (Approval a : proxyActed) {
            mergedMap.putIfAbsent(a.getRequest().getRequestId(), a);
        }

        return mergedMap.values().stream()
                .map(approval -> toResDtoWithRecipients(approval.getRequest()))
                .toList();
    }

//    승인 처리
    public ApprovalRequestResDto approve(UUID companyId, UUID memberId, UUID memberPositionId, UUID approvalId, ApprovalActionReqDto reqDto){
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 라인을 찾을 수 없습니다."));

        if (approval.getApprovalStatus() != LineStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "결재 대기(PENDING) 상태에서만 승인할 수 있습니다.");
        }

        // 권한 확인: 본인 결재 또는 대결
        UUID actualMemberId;
        UUID actualMemberPositionId;

        if (approval.getApproverMemberPositionId().equals(memberPositionId)) {
            // 본인 결재
            actualMemberId = memberId;
            actualMemberPositionId = memberPositionId;
        } else {
            // 대결자인지 확인
            AbsenceProxy proxy = absenceProxyRepository
                    .findActiveProxy(companyId, approval.getApproverMemberId(), LocalDateTime.now())
                    .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN,
                            "본인의 결재건만 처리할 수 있습니다."));

            if (!proxy.getSubstituteId().equals(memberId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN,
                        "본인의 결재건만 처리할 수 있습니다.");
            }

            actualMemberId = memberId;
            actualMemberPositionId = memberPositionId;
        }



//        현재 단계 승인 처리
        approval.approve(reqDto.getComment(), actualMemberId, actualMemberPositionId);

        // 서명 이미지 복사
        try {
            SignatureResDto signatureRes = memberServiceClient.getSignatureUrl(actualMemberId);
            if (signatureRes != null && signatureRes.getSignatureUrl() != null) {
                approval.sign(signatureRes.getSignatureUrl());
            }
        } catch (Exception e) {
            // 서명 조회 실패해도 승인은 진행
        }

        ApprovalRequest request = approval.getRequest();
        UUID requestId = request.getRequestId();

        Integer maxStep = approvalRepository.findMaxStepOrderByRequestId(requestId).orElse(0);

        if (approval.getStepOrder() < maxStep) {
            Approval nextApproval = approvalRepository
                    .findByRequestAndStepOrder(request, approval.getStepOrder() + 1)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "다음 결재 단계를 찾을 수 없습니다."));
            nextApproval.activate();

            if (request.getRequestStatus() == RequestStatus.WAIT) {
                request.updateStatus(RequestStatus.PENDING);
            }

            //        다음 결재자에게 알림
            approvalNotificationService.notifyNextApprover(request, nextApproval, actualMemberId);

        } else {
            request.updateStatus(RequestStatus.APPROVED);
            // 캘린더 연동 이벤트 발행
            publishCalendarEventIfApplicable(request);

            // approve() 메서드 내 최종 승인 처리 블록에 추가
            if (request.getRequestType() == RequestType.OFFICIAL) {
                approvalNotificationService.notifyOfficialReadyToSend(request);
            }

            // 최종 승인 시 이벤트 발행
            publishOvertimeEventIfApplicable(request, OvertimeApprovalEvent.Action.APPROVE);
            publishLeaveEventIfApplicable(request, actualMemberId, LeaveApprovalEvent.Action.USE, null);
            publishAllowanceEventIfApplicable(request, actualMemberId, AllowanceApprovalEvent.Action.APPROVE, reqDto.getComment());
            publishScheduleEventIfApplicable(request, ScheduleApprovalEvent.Action.APPROVE);
            publishLeaveOfAbsenceEventIfApplicable(request, actualMemberId, LeaveOfAbsenceApprovalEvent.Action.APPROVE, null);
            publishResignationEventIfApplicable(request, actualMemberId, ResignationApprovalEvent.Action.APPROVE, null);
            publishAttendanceCorrectionEventIfApplicable(request, actualMemberId, AttendanceCorrectionApprovalEvent.Action.APPROVE, null);
            publishEarlyLeaveEventIfApplicable(request, actualMemberId, EarlyLeaveApprovalEvent.Action.APPROVE, null);
            publishPersonnelOrderEventIfApplicable(request, actualMemberId);
        }

        List<Approval> allApprovals =
                approvalRepository.findByRequestIdWithRequest(requestId);
        List<ApprovalViewer> viewers =
                approvalViewerRepository.findByRequestId(requestId);

        //        최종 승인 완료 시 요청자/참조자/공람자에게 알림
        if (request.getRequestStatus() == RequestStatus.APPROVED) {
            approvalNotificationService.notifyApproved(request, viewers, actualMemberId);
        }

        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL) {
            recipientDtos = officialRecipientRepository
                    .findByApprovalRequest_RequestId(requestId)
                    .stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }

        // 대결자가 처리한 경우 부재자(원래 결재자)에게 알림
        if (!actualMemberId.equals(approval.getApproverMemberId())) {
            String title = request.getApprovalDocument().getDocumentName();
            eventPublisher.publishEvent(NotificationMessage.builder()
                    .receiverId(approval.getApproverMemberId())
                    .senderId(actualMemberId)
                    .notificationType(NotificationType.APPROVAL_PROXY_ACTED)
                    .content(title + " 결재가 대결 처리되었습니다.")
                    .targetId(request.getRequestId())
                    .targetType("APPROVAL")
                    .build());
        }

        // 검색 인덱싱 이벤트 발행
        eventPublisher.publishEvent(new ApprovalChangedEvent(request.getRequestId()));

        return ApprovalRequestResDto.fromEntity(request, allApprovals, viewers, recipientDtos);

    }

    private void publishCalendarEventIfApplicable(ApprovalRequest request) {
        ApprovalDocument document = request.getApprovalDocument();
        if (!"Y".equals(document.getIsCalendarVisibleYn())) {
            return;
        }

        String contentJson = request.getContentJson();
        JsonNode node = readContent(contentJson);
        if (node == null) return;

        // 시작일 추출
        String startField = document.getCalendarStartField();
        String startValue = textOrNull(node, startField);
        if (startValue == null) return;

        LocalDateTime startAt;
        try {
            startAt = parseToLocalDateTime(startValue);
        } catch (Exception e) {
            log.warn("[Calendar] 시작일 파싱 실패. field={}, value={}", startField, startValue);
            return;
        }

        // 종료일 추출 (없으면 시작일과 동일)
        LocalDateTime endAt = startAt;
        String endField = document.getCalendarEndField();
        if (endField != null) {
            String endValue = textOrNull(node, endField);
            if (endValue != null) {
                try {
                    endAt = parseToLocalDateTime(endValue);
                } catch (Exception e) {
                    log.warn("[Calendar] 종료일 파싱 실패. field={}, value={}", endField, endValue);
                }
            }
        }

        // 당일이면 endAt을 해당 날 23:59:59로
        if (startAt.toLocalDate().equals(endAt.toLocalDate())) {
            endAt = endAt.toLocalDate().atTime(23, 59, 59);
            startAt = startAt.toLocalDate().atStartOfDay();
        }

        // title 생성
        String displayName = document.getCalendarDisplayName();
        String title = request.getRequesterName() + " " + displayName;

        String titleField = document.getCalendarTitleField();
        if (titleField != null) {
            String titleExtra = textOrNull(node, titleField);
            if (titleExtra != null && !titleExtra.isBlank()) {
                title = title + " (" + titleExtra + ")";
            }
        }

        calendarApprovalEventPublisher.publish(
                CalendarApprovalEvent.builder()
                        .companyId(document.getCompanyId())
                        .memberId(request.getMemberId())
                        .requesterName(request.getRequesterName())
                        .organizationId(request.getRequesterOrganizationId())
                        .title(title)
                        .startAt(startAt)
                        .endAt(endAt)
                        .requestId(request.getRequestId())
                        .build()
        );
    }

    // LocalDate, LocalDateTime 둘 다 파싱 가능하도록
    private LocalDateTime parseToLocalDateTime(String value) {
        if (value.contains("T")) {
            return LocalDateTime.parse(value);
        }
        return LocalDate.parse(value).atStartOfDay();
    }


    public ApprovalRequestResDto reject(UUID companyId, UUID memberId, UUID memberPositionId, UUID approvalId,
                                        ApprovalActionReqDto reqDto) {

        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 라인을 찾을 수 없습니다."));

        if (approval.getApprovalStatus() != LineStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "결재 대기(PENDING) 상태에서만 반려할 수 있습니다.");
        }

        if (reqDto.getComment() == null || reqDto.getComment().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "반려 사유는 필수입니다.");
        }

        // 권한 확인: 본인 결재 또는 대결
        UUID actualMemberId;
        UUID actualMemberPositionId;

        if (approval.getApproverMemberPositionId().equals(memberPositionId)) {
            actualMemberId = memberId;
            actualMemberPositionId = memberPositionId;
        } else {
            AbsenceProxy proxy = absenceProxyRepository
                    .findActiveProxy(companyId, approval.getApproverMemberId(), LocalDateTime.now())
                    .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN,
                            "본인의 결재건만 처리할 수 있습니다."));

            if (!proxy.getSubstituteId().equals(memberId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN,
                        "본인의 결재건만 처리할 수 있습니다.");
            }

            actualMemberId = memberId;
            actualMemberPositionId = memberPositionId;
        }

        // 현재 결재라인 반려
        approval.reject(reqDto.getComment(), actualMemberId, actualMemberPositionId);

        // 나머지 WAITING 라인 → REJECTED 처리
        ApprovalRequest request = approval.getRequest();
        UUID requestId = request.getRequestId();

        List<Approval> allApprovals = approvalRepository.findByRequestIdWithRequest(requestId);
        for (Approval a : allApprovals) {
            if (a.getApprovalStatus() == LineStatus.WAITING) {
                a.reject(null, null, null);
            }
        }

        // 결재요청 상태 -> REJECTED
        request.updateStatus(RequestStatus.REJECTED);

        // 반려 시 이벤트 발행
        publishOvertimeEventIfApplicable(request, OvertimeApprovalEvent.Action.REJECT);
        publishAllowanceEventIfApplicable(request, actualMemberId, AllowanceApprovalEvent.Action.REJECT, reqDto.getComment());
        publishLeaveEventIfApplicable(request, actualMemberId, LeaveApprovalEvent.Action.REJECT, reqDto.getComment());
        publishLeaveOfAbsenceEventIfApplicable(request, actualMemberId,
                LeaveOfAbsenceApprovalEvent.Action.REJECT, reqDto.getComment());
        publishResignationEventIfApplicable(request, actualMemberId,
                ResignationApprovalEvent.Action.REJECT, reqDto.getComment());
        publishAttendanceCorrectionEventIfApplicable(request, actualMemberId,
                AttendanceCorrectionApprovalEvent.Action.REJECT, reqDto.getComment());
        publishEarlyLeaveEventIfApplicable(request, actualMemberId,
                EarlyLeaveApprovalEvent.Action.REJECT, reqDto.getComment());

        // 반려 시 REJECT 이벤트 발행
        publishLeaveEventIfApplicable(request, actualMemberId,
                LeaveApprovalEvent.Action.REJECT, reqDto.getComment());
        publishScheduleEventIfApplicable(request, ScheduleApprovalEvent.Action.REJECT);

        List<ApprovalViewer> viewers = approvalViewerRepository.findByRequestId(requestId);

        // 요청자/참조자에게 반려 알림
        approvalNotificationService.notifyRejected(
                request, viewers, actualMemberId, reqDto.getComment());

        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL){
            recipientDtos = officialRecipientRepository
                    .findByApprovalRequest_RequestId(requestId)
                    .stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }

        // 대결자가 처리한 경우 부재자(원래 결재자)에게 알림
        if (!actualMemberId.equals(approval.getApproverMemberId())) {
            String title = request.getApprovalDocument().getDocumentName();
            eventPublisher.publishEvent(NotificationMessage.builder()
                    .receiverId(approval.getApproverMemberId())
                    .senderId(actualMemberId)
                    .notificationType(NotificationType.APPROVAL_PROXY_ACTED)
                    .content(title + " 결재가 대결 처리되었습니다.")
                    .targetId(request.getRequestId())
                    .targetType("APPROVAL")
                    .build());
        }

        // 검색 인덱스 이벤트 발행
        eventPublisher.publishEvent(new ApprovalChangedEvent(request.getRequestId()));

        return ApprovalRequestResDto.fromEntity(request, allApprovals, viewers, recipientDtos);
    }

    // 결재 대기함 (내 건 + 대결 건)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findPending(UUID companyId, UUID memberId, UUID memberPositionId) {

        // 1) 내가 직접 결재할 건
        List<Approval> myPending =
                approvalRepository.findPendingByMemberPositionId(memberPositionId, LineStatus.PENDING);

        // 2) 내가 대결자로 지정된 부재자들의 PENDING 건
        List<AbsenceProxy> activeProxies =
                absenceProxyRepository.findBySubstituteIdAndIsActiveYn(memberId, "Y");

        List<Approval> proxyPending = activeProxies.stream()
                .filter(proxy -> {
                    // 현재 시점에 유효한 위임만
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(proxy.getStartDate()) && !now.isAfter(proxy.getEndDate())
                            && proxy.getCompanyId().equals(companyId);
                })
                .flatMap(proxy ->
                        approvalRepository.findPendingByApproverMemberId(
                                proxy.getMemberId(), LineStatus.PENDING).stream()
                )
                .toList();

        // 3) 합산 (중복 제거 — requestId 기준)
        Map<UUID, Approval> mergedMap = new LinkedHashMap<>();
        for (Approval a : myPending) {
            mergedMap.putIfAbsent(a.getRequest().getRequestId(), a);
        }
        for (Approval a : proxyPending) {
            mergedMap.putIfAbsent(a.getRequest().getRequestId(), a);
        }

        return mergedMap.values().stream()
                .map(approval -> toResDtoWithRecipients(approval.getRequest()))
                .toList();
    }

    // 결재 예정함 (아직 내 차례가 아닌 문서)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findWaiting(UUID memberPositionId) {

        List<Approval> waitingApprovals =
                approvalRepository.findWaitingByMemberPositionId(memberPositionId, LineStatus.WAITING);

        return waitingApprovals.stream()
                .map(approval -> toResDtoWithRecipients(approval.getRequest()))
                .toList();
    }

    // 결재함 전체 (PENDING + WAITING 통합)
    @Transactional(readOnly = true)
    public List<ApprovalRequestResDto> findAllInbox(UUID companyId, UUID memberId, UUID memberPositionId) {

        // 1) PENDING (내 차례 + 대결)
        List<ApprovalRequestResDto> pendingList = findPending(companyId, memberId, memberPositionId);

        // 2) WAITING (예정)
        List<ApprovalRequestResDto> waitingList = findWaiting(memberPositionId);

        // 3) 합산 (중복 제거 — requestId 기준, PENDING 우선)
        Map<UUID, ApprovalRequestResDto> mergedMap = new LinkedHashMap<>();
        for (ApprovalRequestResDto dto : pendingList) {
            mergedMap.putIfAbsent(dto.getRequestId(), dto);
        }
        for (ApprovalRequestResDto dto : waitingList) {
            mergedMap.putIfAbsent(dto.getRequestId(), dto);
        }

        return new ArrayList<>(mergedMap.values());
    }

    private ApprovalRequestResDto toResDtoWithRecipients(ApprovalRequest request) {
        UUID requestId = request.getRequestId();
        List<Approval> allApprovals =
                approvalRepository.findByRequestIdWithRequest(requestId);
        List<ApprovalViewer> viewers =
                approvalViewerRepository.findByRequestId(requestId);

        List<OfficialRecipientResDto> recipientDtos = null;
        if (request.getRequestType() == RequestType.OFFICIAL) {
            recipientDtos = officialRecipientRepository
                    .findByApprovalRequest_RequestId(requestId)
                    .stream()
                    .map(OfficialRecipientResDto::fromEntity)
                    .toList();
        }
        return ApprovalRequestResDto.fromEntity(request, allApprovals, viewers, recipientDtos);
    }


    // 아래는 승인/반려 직후 Kafka로 이벤트 쏘는 헬퍼들

    // 연장근무신청 문서일 때만 overtime 이벤트 발행
    // contentJson에서 workType(연장/야간/휴일) 뽑아서 같이 실어 보낸다.
    private void publishOvertimeEventIfApplicable(ApprovalRequest request, OvertimeApprovalEvent.Action action) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"연장근무신청".equals(docName)) {
            return;
        }

        String workType = extractWorkType(request.getContentJson());

        overtimeApprovalEventPublisher.publish(
                OvertimeApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .workType(workType)
                        .startAt(request.getStartDateTime())
                        .endAt(request.getEndDateTime())
                        .action(action)
                        .build()
        );
    }

    // contentJson에서 workType 한 필드만 뽑기
    // JSON 파싱 실패하거나 필드 없으면 그냥 null 리턴 (이벤트는 나가되 workType만 비게 됨)
    private String extractWorkType(String contentJson) {
        if (contentJson == null) return null;
        try {

            JsonNode node = objectMapper.readTree(contentJson);
            JsonNode wt = node.get("workType");
            return wt != null ? wt.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // 휴가신청서 문서일 때만 leave 이벤트 발행
    // 연차=1일씩 누적, 반차=0.5일. BalanceType은 일단 ANNUAL 고정 (CARRYOVER 우선소진은 salary 쪽에서 처리)
    private void publishLeaveEventIfApplicable(ApprovalRequest request,
                                               UUID approverId,
                                               LeaveApprovalEvent.Action action,
                                               String note) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"휴가신청서".equals(docName)) return;

        JsonNode node = readContent(request.getContentJson());
        if (node == null) return;

        UUID leaveRequestId = extractLeaveRequestId(node);
        if (leaveRequestId == null) return;  // pre-action 누락 시 무시
        LocalDate startDate = dateOrNull(node, "startDate");

        leaveApprovalEventPublisher.publish(
                LeaveApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .leaveRequestId(leaveRequestId)
                        .leaveDate(startDate)
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .note(note)
                        .action(action)
                        .build()
        );
    }

    /**
     * 인사발령 문서일 때
     */
    private void publishPersonnelOrderEventIfApplicable(ApprovalRequest request, UUID approverId) {
        String docName = request.getApprovalDocument().getDocumentName();
        log.info("[PersonnelOrder][DBG] publishPersonnelOrderEventIfApplicable 진입. requestId={} docName='{}'",
                request.getRequestId(), docName);
        // 종류(전보/승진/강등/복합/정기) 구분
        if (docName == null) {
            log.warn("[PersonnelOrder][DBG] docName=null -> skip");
            return;
        }
        boolean isPersonnelOrder = "인사발령품의서".equals(docName)
                || docName.startsWith("인사발령")
                || "정기 인사발령".equals(docName);
        if (!isPersonnelOrder) {
            log.warn("[PersonnelOrder][DBG] docName 매칭 실패 '{}' -> skip", docName);
            return;
        }

        JsonNode node = readContent(request.getContentJson());
        if (node == null) {
            log.warn("[PersonnelOrder][DBG] contentJson 파싱 실패 -> skip. requestId={}", request.getRequestId());
            return;
        }

        LocalDate effectiveDate = dateOrNull(node, "effectiveDate");
        if (effectiveDate == null) effectiveDate = LocalDate.now();

        JsonNode itemsNode = node.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("[PersonnelOrder] items 비어있음 - 발령 이벤트 skip. requestId={}", request.getRequestId());
            return;
        }

        List<PersonnelOrderApprovedEvent.Item> items = new ArrayList<>();
        for (JsonNode it : itemsNode) {
            UUID memberId = uuidOrNull(it, "memberId");
            if (memberId == null) continue;
            items.add(PersonnelOrderApprovedEvent.Item.builder()
                    .memberId(memberId)
                    .memberName(textOrNull(it, "memberName"))
                    .orderType(textOrNull(it, "orderType"))
                    .beforeOrganizationId(uuidOrNull(it, "beforeOrganizationId"))
                    .afterOrganizationId(uuidOrNull(it, "afterOrganizationId"))
                    .beforeOrganizationName(textOrNull(it, "beforeOrganizationName"))
                    .afterOrganizationName(textOrNull(it, "afterOrganizationName"))
                    .beforeJobGradeName(textOrNull(it, "beforeJobGradeName"))
                    .afterJobGradeName(textOrNull(it, "afterJobGradeName"))
                    .beforeJobTitleName(textOrNull(it, "beforeJobTitleName"))
                    .afterJobTitleName(textOrNull(it, "afterJobTitleName"))
                    .reason(textOrNull(it, "reason"))
                    .build());
        }
        if (items.isEmpty()) {
            log.warn("[PersonnelOrder] 유효 item 없음 - skip. requestId={}", request.getRequestId());
            return;
        }

        personnelOrderApprovalEventPublisher.publish(
                PersonnelOrderApprovedEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .approvalDocumentId(request.getApprovalDocument().getDocumentId())
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .effectiveDate(effectiveDate)
                        .items(items)
                        .build()
        );
    }

    // 사직서 문서일 때만 resignation 이벤트 발행
    private void publishResignationEventIfApplicable(ApprovalRequest request,
                                                     UUID approverId,
                                                     ResignationApprovalEvent.Action action,
                                                     String note) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"사직서".equals(docName)) return;

        JsonNode node = readContent(request.getContentJson());
        if (node == null) return;

        LocalDate resignDate = dateOrNull(node, "resignDate");
        String reason = textOrNull(node, "resignReason");
        String detail = textOrNull(node, "detail");

        resignationApprovalEventPublisher.publish(
                ResignationApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .resignDate(resignDate)
                        .resignReason(reason)
                        .detail(detail)
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .note(note)
                        .action(action)
                        .build()
        );
    }

    // 조퇴계 - 승인 시 퇴근시간 정정 + 정규 8h 보정
    private void publishEarlyLeaveEventIfApplicable(ApprovalRequest request,
                                                     UUID approverId,
                                                     EarlyLeaveApprovalEvent.Action action,
                                                     String note) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"조퇴계".equals(docName)) return;

        JsonNode node = readContent(request.getContentJson());
        if (node == null) return;

        LocalDate date = dateOrNull(node, "attendanceDate");
        if (date == null) {
            log.warn("[EarlyLeave] 조퇴 일자 누락, requestId={}", request.getRequestId());
            return;
        }

        earlyLeaveApprovalEventPublisher.publish(
                EarlyLeaveApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .attendanceDate(date)
                        .earlyLeaveAt(combineDateTime(date, textOrNull(node, "earlyLeaveTime")))
                        .reason(textOrNull(node, "reason"))
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .note(note)
                        .action(action)
                        .build()
        );
    }

    // 근태정정신청 - 출/퇴근시각은 HH:mm 시간만 받아 attendanceDate 와 결합
    private void publishAttendanceCorrectionEventIfApplicable(ApprovalRequest request,
                                                              UUID approverId,
                                                              AttendanceCorrectionApprovalEvent.Action action,
                                                              String note) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"근태정정신청".equals(docName)) return;

        JsonNode node = readContent(request.getContentJson());
        if (node == null) return;

        LocalDate date = dateOrNull(node, "attendanceDate");
        if (date == null) {
            log.warn("[AttendanceCorrection] 정정 일자 누락, requestId={}", request.getRequestId());
            return;
        }

        attendanceCorrectionApprovalEventPublisher.publish(
                AttendanceCorrectionApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .attendanceDate(date)
                        .requestedClockIn(combineDateTime(date, textOrNull(node, "requestedClockIn")))
                        .requestedClockOut(combineDateTime(date, textOrNull(node, "requestedClockOut")))
                        .reason(textOrNull(node, "reason"))
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .note(note)
                        .action(action)
                        .build()
        );
    }

    // HH:mm 또는 HH:mm:ss 시간 문자열을 attendanceDate 와 결합해 LocalDateTime 생성, 빈 값/포맷 이상이면 null
    private LocalDateTime combineDateTime(LocalDate date, String time) {
        if (date == null || time == null || time.isBlank()) return null;
        try {
            return LocalDateTime.of(date, LocalTime.parse(time));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 연차/반차 류인지 판단, 잔고 차감 필요 여부
     */
    private boolean isAnnualLike(String vacationType) {
        return vacationType.equals("연차") || vacationType.startsWith("반차");
    }

    /**
     * contentJson 에서 leaveRequestId 추출
     */
    private UUID extractLeaveRequestId(JsonNode node) {
        String raw = node.path("leaveRequestId").asText(null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    // 휴가 일수 계산 - 반차는 무조건 0.5, 연차는 시작/종료일 포함 일수
    // endDate 비어있으면 당일치기로 보고 startDate만으로 1일 처리
    private double calculateLeaveDays(String vacationType, LocalDate startDate, LocalDate endDate) {
        if (vacationType.startsWith("반차")) return 0.5;
        LocalDate end = (endDate != null) ? endDate : startDate;
        return end.toEpochDay() - startDate.toEpochDay() + 1.0;
    }

    // contentJson 통째로 파싱 - 실패하면 null 돌려서 호출한 쪽이 발행 스킵.
    private JsonNode readContent(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    // 해당 필드가 없거나 명시적 null이면 null 반환하는 얇은 래퍼
    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    // ISO yyyy-MM-dd 문자열을 LocalDate로 파싱 - 빈 문자열/포맷 이상이면 null
    private LocalDate dateOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    // UUID 문자열 파싱
    private UUID uuidOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null || text.isBlank()) return null;
        try {
            return UUID.fromString(text);
        } catch (Exception e) {
            return null;
        }
    }

    // yyyy-MM-dd'T'HH:mm 문자열을 LocalDateTime으로 파싱
    private LocalDateTime dateTimeOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 시차출퇴근 스케줄 선택 문서일 때만 schedule 이벤트 발행
     */
    private void publishScheduleEventIfApplicable(ApprovalRequest request,
                                                  ScheduleApprovalEvent.Action action) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"출퇴근시간 변경 신청서".equals(docName)) {
            return;
        }

        UUID selectionId = extractSelectionId(request.getContentJson());
        if (selectionId == null) {
            return;
        }

        scheduleApprovalEventPublisher.publish(
                ScheduleApprovalEvent.builder()
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .requestId(request.getRequestId())
                        .selectionId(selectionId)
                        .approverId(request.getMemberId())
                        .decidedAt(LocalDateTime.now())
                        .note(null)
                        .action(action)
                        .build()
        );
    }

    // contentJson 에서 selectionId 추출
    private UUID extractSelectionId(String contentJson) {
        JsonNode node = readContent(contentJson);
        if (node == null) return null;
        try {
            String raw = node.path("selectionId").asText(null);
            return raw == null ? null : UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 수당 변경 신청 문서일 때만 allowance 이벤트 발행
     */
    private void publishAllowanceEventIfApplicable(ApprovalRequest request,
                                                   UUID approverId,
                                                   AllowanceApprovalEvent.Action action,
                                                   String note) {

        String docName = request.getApprovalDocument().getDocumentName();
        if (!"수당 변경 신청".equals(docName)) {
            return;
        }

        UUID memberAllowanceId = extractMemberAllowanceId(request.getContentJson());
        if (memberAllowanceId == null) return;

        allowanceApprovalEventPublisher.publish(AllowanceApprovalEvent.builder()
                .companyId(request.getApprovalDocument().getCompanyId())
                .memberId(request.getMemberId())
                .requestId(request.getRequestId())
                .approverId(approverId)
                .decidedAt(LocalDateTime.now())
                .note(note)
                .action(action)
                .build());
    }

    // contentJson 에서 memberAllowanceId 추출
    private UUID extractMemberAllowanceId(String contentJson) {
        JsonNode node = readContent(contentJson);
        if (node == null) return null;
        try {
            String raw = node.path("memberAllowanceId").asText(null);
            return raw == null ? null : UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 휴직 신청서 문서일 때만 leave-of-absence 이벤트 발행
     */
    private void publishLeaveOfAbsenceEventIfApplicable(ApprovalRequest request,
                                                        UUID approverId,
                                                        LeaveOfAbsenceApprovalEvent.Action action,
                                                        String note) {
        String docName = request.getApprovalDocument().getDocumentName();
        if (!"휴직 신청서".equals(docName)) {
            return;
        }

        JsonNode node = readContent(request.getContentJson());
        if (node == null) return;

        // contentJson 에 심어진 leaveOfAbsenceId 추출
        UUID leaveOfAbsenceId = extractLeaveOfAbsenceId(node);
        if (leaveOfAbsenceId == null) {
            return;
        }

        leaveOfAbsenceApprovalEventPublisher.publish(
                LeaveOfAbsenceApprovalEvent.builder()
                        .requestId(request.getRequestId())
                        .leaveOfAbsenceId(leaveOfAbsenceId)
                        .companyId(request.getApprovalDocument().getCompanyId())
                        .memberId(request.getMemberId())
                        .approverId(approverId)
                        .decidedAt(LocalDateTime.now())
                        .note(note)
                        .action(action)
                        .build()
        );
    }

    /**
     * contentJson 에서 leaveOfAbsenceId 추출
     */
    private UUID extractLeaveOfAbsenceId(JsonNode node) {
        String raw = node.path("leaveOfAbsenceId").asText(null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
