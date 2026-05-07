package com._team._team.approval.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.approval.dto.reqdto.ApprovalDocumentCreateReqDto;
import com._team._team.approval.dto.reqdto.ApprovalDocumentUpdateReqDto;
import com._team._team.approval.dto.resdto.ApprovalDocumentResDto;
import com._team._team.approval.service.ApprovalDocumentService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/documents")
public class ApprovalDocumentController {

    private final ApprovalDocumentService approvalDocumentService;

    @Autowired
    public ApprovalDocumentController(ApprovalDocumentService approvalDocumentService) {
        this.approvalDocumentService = approvalDocumentService;
    }

    // 결재 양식 생성
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.CREATE)
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody ApprovalDocumentCreateReqDto reqDto) {
        ApprovalDocumentResDto resDto = approvalDocumentService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 결재 양식 수정
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.UPDATE)
    @PutMapping("/{documentId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID documentId,
            @Valid @RequestBody ApprovalDocumentUpdateReqDto reqDto) {
        ApprovalDocumentResDto resDto = approvalDocumentService.update(companyId, documentId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    // 내부용: 기본 결재양식 자동 생성
    @PostMapping("/init")
    public void initDefaultDocuments(@RequestParam UUID companyId) {
        approvalDocumentService.initDefaultDocuments(companyId);
    }

    // 결재 양식 단건 조회
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.READ)
    @GetMapping("/{documentId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID documentId) {
        ApprovalDocumentResDto resDto = approvalDocumentService.findById(companyId, documentId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식 조회 성공"),
                HttpStatus.OK
        );
    }

    // 회사별 활성 양식 목록 조회
    @GetMapping("/active")
    public ResponseEntity<?> findActive(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ApprovalDocumentResDto> result = approvalDocumentService.findActiveByCompanyId(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "활성 결재 양식 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 회사별 전체 양식 목록 조회 (관리자용)
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.READ)
    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ApprovalDocumentResDto> result = approvalDocumentService.findAllByCompanyId(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "전체 결재 양식 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 결재 양식 활성화
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.UPDATE)
    @PatchMapping("/{documentId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID documentId) {
        ApprovalDocumentResDto resDto = approvalDocumentService.activate(companyId, documentId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식이 활성화되었습니다."),
                HttpStatus.OK
        );
    }

    // 결재 양식 비활성화
    @CheckPermission(resource = Resource.APPROVAL_AD, action = Action.UPDATE)
    @PatchMapping("/{documentId}/deactivate")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID documentId) {
        ApprovalDocumentResDto resDto = approvalDocumentService.deactivate(companyId, documentId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식이 비활성화되었습니다."),
                HttpStatus.OK
        );
    }
    // 내부 통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findActiveByCompanyIdInternal(@RequestParam UUID companyId) {
        List<ApprovalDocumentResDto> result = approvalDocumentService.findActiveByCompanyId(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "활성 결재 양식 목록 조회 (내부)"),
                HttpStatus.OK);
    }

    // 내부 서비스용 결재 양식 단건 조회 (권한 체크 없음)
    @GetMapping("/internal/{documentId}")
    public ResponseEntity<?> findByIdInternal(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID documentId) {
        ApprovalDocumentResDto resDto = approvalDocumentService.findById(companyId, documentId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "결재 양식 조회 성공 (내부)"),
                HttpStatus.OK
        );
    }
}
