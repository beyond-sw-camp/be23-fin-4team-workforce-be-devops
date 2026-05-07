package com._team._team.contract.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.dto.reqdto.*;
import com._team._team.contract.dto.resdto.ContractBatchResDto;
import com._team._team.contract.dto.resdto.ContractResDto;
import com._team._team.contract.service.ContractService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contract/contracts")
public class ContractController {

    private final ContractService contractService;

    @Autowired
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    //    발송 (인사팀)
    // 개별 발송
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/send")
    public ResponseEntity<?> send(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody ContractSendReqDto reqDto) {
        ContractResDto resDto = contractService.sendToEmployee(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서가 발송되었습니다."),
                HttpStatus.CREATED);
    }

    // 일괄 발송
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/send-batch")
    public ResponseEntity<?> sendBatch(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody ContractBatchSendReqDto reqDto) {
        ContractBatchResDto resDto = contractService.sendBatch(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서가 일괄 발송되었습니다."),
                HttpStatus.CREATED);
    }

//    서명 (직원)
    // 서명
    @PostMapping("/{contractId}/sign")
    public ResponseEntity<?> sign(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId,
            @RequestBody ContractSignReqDto reqDto) {
        ContractResDto resDto = contractService.sign(companyId, memberId, contractId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "서명이 완료되었습니다."),
                HttpStatus.OK);
    }

    // ===================== 조회 =====================

    // 내 계약 목록 (직원용)
    @GetMapping("/my")
    public ResponseEntity<?> findMyContracts(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(required = false) ContractStatus status) {
        List<ContractResDto> result = contractService.findMyContracts(memberId, status);
        return new ResponseEntity<>(
                ApiResponse.success(result, "내 계약 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 계약 상세 (인사팀용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping("/{contractId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID contractId) {
        ContractResDto resDto = contractService.findById(companyId, contractId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약 상세 조회 성공"),
                HttpStatus.OK);
    }

    // 계약 상세 (직원용 — 본인 계약만)
    @GetMapping("/{contractId}/my")
    public ResponseEntity<?> findMyContractById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId) {
        ContractResDto resDto = contractService.findMyContractById(companyId, memberId, contractId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약 상세 조회 성공"),
                HttpStatus.OK);
    }

    // 회사별 전체 계약 목록 (인사팀용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ContractResDto> result = contractService.findAllByCompanyId(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "전체 계약 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 배치 목록 (인사팀용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping("/batches")
    public ResponseEntity<?> findBatches(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ContractBatchResDto> result = contractService.findBatches(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "배치 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 배치별 계약 목록 (인사팀용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<?> findByBatchId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID batchId) {
        List<ContractResDto> result = contractService.findByBatchId(companyId, batchId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "배치별 계약 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 직원 계약 거절
    @PostMapping("/{contractId}/reject")
    public ResponseEntity<?> reject(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId,
            @RequestBody ContractRejectReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(contractService.reject(companyId, memberId, contractId, reqDto), "계약 거절 성공"),
                HttpStatus.OK
        );
    }

    // 계약 회수 (인사팀)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/{contractId}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId,
            @RequestBody ContractCancelReqDto reqDto) {
        ContractResDto resDto = contractService.cancel(companyId, memberId, contractId, reqDto.getCancelReason());
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약이 회수되었습니다."),
                HttpStatus.OK);
    }

    // 개별 재발송
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/{contractId}/resend")
    public ResponseEntity<?> resend(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId,
            @RequestBody ContractResendReqDto reqDto) {
        ContractResDto resDto = contractService.resend(companyId, memberId, contractId, reqDto.getAdminInputJson());
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서가 재발송되었습니다."),
                HttpStatus.CREATED);
    }

    // 배치 재발송
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/batches/{batchId}/resend")
    public ResponseEntity<?> resendBatch(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID batchId,
            @Valid @RequestBody ContractBatchResendReqDto reqDto) {
        ContractBatchResDto resDto = contractService.resendBatch(companyId, memberId, batchId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서가 일괄 재발송되었습니다."),
                HttpStatus.CREATED);
    }

    // 이력 조회 (인사팀용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping("/{contractId}/history")
    public ResponseEntity<?> findHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID contractId) {
        List<ContractResDto> result = contractService.findHistory(companyId, contractId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "계약 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 이력 조회 (직원용 — 본인 계약만)
    @GetMapping("/{contractId}/history/my")
    public ResponseEntity<?> findMyContractHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId) {
        List<ContractResDto> result = contractService.findMyContractHistory(companyId, memberId, contractId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "계약 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 개별 서명 리마인드
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/{contractId}/remind")
    public ResponseEntity<?> remind(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID contractId) {
        contractService.remindSign(companyId, memberId, contractId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "서명 리마인드 알림이 발송되었습니다."),
                HttpStatus.OK);
    }

    // 배치 미서명자 일괄 리마인드
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping("/batches/{batchId}/remind")
    public ResponseEntity<?> remindBatch(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID batchId) {
        int count = contractService.remindBatch(companyId, memberId, batchId);
        return new ResponseEntity<>(
                ApiResponse.success(count, count + "명에게 리마인드 알림이 발송되었습니다."),
                HttpStatus.OK);
    }
}
