package com._team._team.approval.controller;

import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.RequestType;
import com._team._team.approval.dto.reqdto.ApprovalRequestCreateReqDto;
import com._team._team.approval.dto.reqdto.CancelReqDto;
import com._team._team.approval.dto.resdto.ApprovalRequestResDto;
import com._team._team.approval.service.ApprovalRequestService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/approval/requests")
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @Autowired
    public ApprovalRequestController(ApprovalRequestService approvalRequestService) {
        this.approvalRequestService = approvalRequestService;
    }

    // 결재요청 생성
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @Valid @RequestBody ApprovalRequestCreateReqDto reqDto) {
        ApprovalRequestResDto resDto = approvalRequestService.create(companyId, memberId, memberPositionId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 요청이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 결재요청 상세 조회
    @GetMapping("/{requestId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID requestId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        ApprovalRequestResDto resDto = approvalRequestService.findById(companyId, memberId, memberPositionId, requestId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 요청 조회 성공"),
                HttpStatus.OK
        );
    }

    // 내가 올린 결재 목록 (상태별 필터 optional)
    @GetMapping("/my")
    public ResponseEntity<?> findMyRequests(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType requestType) {
        List<ApprovalRequestResDto> result =
                approvalRequestService.findMyRequests(memberId, status, requestType);
        return new ResponseEntity<>(
                ApiResponse.success(result, "내 결재 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 임시저장 수정
    @PatchMapping("/{requestId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID requestId,
            @Valid @RequestBody ApprovalRequestCreateReqDto reqDto) {
        ApprovalRequestResDto resDto =
                approvalRequestService.update(companyId, memberId,memberPositionId, requestId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 요청이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    // 결재 취소
    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID requestId,
            @Valid @RequestBody CancelReqDto reqDto) {
        ApprovalRequestResDto resDto =
                approvalRequestService.cancel(companyId, memberId, requestId, reqDto.getCancelReason());
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재가 취소되었습니다."),
                HttpStatus.OK
        );
    }

    // 부서 문서함 (본인 부서 + 하위 조직의 결재 요청 모아보기)
    @GetMapping("/department")
    public ResponseEntity<?> findDepartmentRequests(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @RequestParam UUID organizationId,
            @RequestParam(required = false) RequestType requestType) {
        List<ApprovalRequestResDto> result = approvalRequestService
                .findDepartmentRequests(companyId, memberPositionId, memberId, organizationId, requestType);
        return new ResponseEntity<>(
                ApiResponse.success(result, "부서 문서함 조회 성공"),
                HttpStatus.OK
        );
    }

    // 공문 수신함 (내 부서가 수신한 공문 목록)
    @GetMapping("/official/received")
    public ResponseEntity<?> findReceivedOfficials(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        List<ApprovalRequestResDto> result = approvalRequestService
                .findReceivedOfficials(companyId, memberPositionId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "공문 수신함 조회 성공"),
                HttpStatus.OK
        );
    }

    // 공문 발송
    @PostMapping("/{requestId}/send-official")
    public ResponseEntity<?> sendOfficial(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID requestId) {
        ApprovalRequestResDto resDto = approvalRequestService.sendOfficial(companyId, memberId, memberPositionId, requestId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "공문이 발송되었습니다."),
                HttpStatus.OK
        );
    }

    @GetMapping("/{requestId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID requestId) {

        byte[] pdf = approvalRequestService.downloadPdf(companyId, memberId, requestId);

        String filename = "approval-" + requestId + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

}
