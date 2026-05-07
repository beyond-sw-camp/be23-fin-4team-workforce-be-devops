package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.SalaryNegotiationBulkCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryNegotiationCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryNegotiationUpdateReqDto;
import com._team._team.salary.dto.resdto.SalaryNegotiationResDto;
import com._team._team.salary.service.SalaryNegotiationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 연봉 협상 컨트롤러
 */
@RestController
@RequestMapping("/salary/negotiations")
public class SalaryNegotiationController {

    private final SalaryNegotiationService service;

    @Autowired
    public SalaryNegotiationController(SalaryNegotiationService service) {
        this.service = service;
    }

    // 조회
    @GetMapping
    public ResponseEntity<?> listByCompany(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<SalaryNegotiationResDto> data = service.listByCompany(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "연봉 협상 목록 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> listByGroup(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID groupId) {
        List<SalaryNegotiationResDto> data = service.listByGroup(companyId, groupId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "시즌 단위 협상 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/{negotiationId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID negotiationId) {
        SalaryNegotiationResDto data = service.findById(companyId, negotiationId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "협상 단건 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/my")
    public ResponseEntity<?> listMine(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        List<SalaryNegotiationResDto> data = service.listMine(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "내 협상 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 등록
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody SalaryNegotiationCreateReqDto reqDto) {
        SalaryNegotiationResDto data = service.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "연봉 협상이 등록되었습니다."),
                HttpStatus.CREATED);
    }

    @PostMapping("/bulk-create")
    public ResponseEntity<?> bulkCreate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody SalaryNegotiationBulkCreateReqDto reqDto) {
        List<SalaryNegotiationResDto> data = service.bulkCreate(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "정기 협상 시즌이 일괄 등록되었습니다."),
                HttpStatus.CREATED);
    }

    // 수정
    @PutMapping("/{negotiationId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID negotiationId,
            @Valid @RequestBody SalaryNegotiationUpdateReqDto reqDto) {
        SalaryNegotiationResDto data = service.update(companyId, negotiationId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "협상안이 수정되었습니다."),
                HttpStatus.OK);
    }

    // 상태 전이
    @PatchMapping("/{negotiationId}/submit")
    public ResponseEntity<?> submit(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID negotiationId) {
        SalaryNegotiationResDto data = service.submit(companyId, negotiationId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "직원에게 통보되었습니다."),
                HttpStatus.OK);
    }

    @PatchMapping("/{negotiationId}/approve")
    public ResponseEntity<?> approve(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID approverId,
            @PathVariable UUID negotiationId,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        SalaryNegotiationResDto data = service.approve(companyId, negotiationId, approverId, note);
        return new ResponseEntity<>(
                ApiResponse.success(data, "협상이 승인되었습니다."),
                HttpStatus.OK);
    }

    @PatchMapping("/{negotiationId}/apply")
    public ResponseEntity<?> apply(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID negotiationId) {
        SalaryNegotiationResDto data = service.apply(companyId, negotiationId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "Salary 새 행이 생성되었습니다."),
                HttpStatus.OK);
    }

    // 직원 본인 응답 - 수락
    @PatchMapping("/my/{negotiationId}/accept")
    public ResponseEntity<?> acceptByEmployee(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID negotiationId,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        SalaryNegotiationResDto data = service.acceptByEmployee(companyId, memberId, negotiationId, note);
        return new ResponseEntity<>(
                ApiResponse.success(data, "협상 제안을 수락했습니다."),
                HttpStatus.OK);
    }

    // 직원 본인 응답 - 거절
    @PatchMapping("/my/{negotiationId}/reject")
    public ResponseEntity<?> rejectByEmployee(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID negotiationId,
            @RequestBody Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        SalaryNegotiationResDto data = service.rejectByEmployee(companyId, memberId, negotiationId, reason);
        return new ResponseEntity<>(
                ApiResponse.success(data, "협상 제안을 거절했습니다."),
                HttpStatus.OK);
    }

    @DeleteMapping("/{negotiationId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID negotiationId) {
        service.delete(companyId, negotiationId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "협상이 삭제되었습니다."),
                HttpStatus.OK);
    }
}
