package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.RetirementPolicyCreateReqDto;
import com._team._team.salary.dto.reqdto.RetirementPolicyUpdateReqDto;
import com._team._team.salary.dto.resdto.RetirementPolicyResDto;
import com._team._team.salary.service.RetirementPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 회사별 퇴직급여 제도 정책
@RestController
@RequestMapping("/salary/retirement-policy")
@RequiredArgsConstructor
public class RetirementPolicyController {

    private final RetirementPolicyService retirementPolicyService;

    // 정책 등록
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody RetirementPolicyCreateReqDto reqDto) {
        RetirementPolicyResDto data = retirementPolicyService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "퇴직급여 정책 등록 성공"),
                HttpStatus.CREATED);
    }

    // 정책 수정
    @PatchMapping("/{policyId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId,
            @Valid @RequestBody RetirementPolicyUpdateReqDto reqDto) {
        RetirementPolicyResDto data = retirementPolicyService.update(companyId, policyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "퇴직급여 정책 수정 성공"),
                HttpStatus.OK);
    }

    // 정책 삭제
    @DeleteMapping("/{policyId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId) {
        retirementPolicyService.delete(companyId, policyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "퇴직급여 정책 삭제 성공"),
                HttpStatus.OK);
    }

    // 회사 전체 정책 이력 조회
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("X-User-CompanyId") UUID companyId) {
        List<RetirementPolicyResDto> data = retirementPolicyService.listByCompany(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "퇴직급여 정책 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 활성 정책 조회 없으면 기본 LEGAL 자동 생성
    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestHeader("X-User-CompanyId") UUID companyId) {
        RetirementPolicyResDto data = retirementPolicyService.getOrCreateActive(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "활성 퇴직급여 정책 조회 성공"),
                HttpStatus.OK);
    }
}