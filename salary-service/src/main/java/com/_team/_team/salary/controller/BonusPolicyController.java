package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.BonusPolicyCreateReqDto;
import com._team._team.salary.dto.reqdto.BonusPolicyUpdateReqDto;
import com._team._team.salary.dto.resdto.BonusPolicyResDto;
import com._team._team.salary.service.BonusPolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 회사별 보너스 (정기상여 / 성과급 / 명절상여) 정책
@RestController
@RequestMapping("/salary/bonus-policy")
public class BonusPolicyController {

    private final BonusPolicyService bonusPolicyService;

    @Autowired
    public BonusPolicyController(BonusPolicyService bonusPolicyService) {
        this.bonusPolicyService = bonusPolicyService;
    }

    // 정책 등록
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody BonusPolicyCreateReqDto reqDto) {
        BonusPolicyResDto data = bonusPolicyService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "보너스 정책 등록 성공"),
                HttpStatus.CREATED);
    }

    // 정책 수정
    @PatchMapping("/{policyId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId,
            @Valid @RequestBody BonusPolicyUpdateReqDto reqDto) {
        BonusPolicyResDto data = bonusPolicyService.update(companyId, policyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "보너스 정책 수정 성공"),
                HttpStatus.OK);
    }

    // 정책 삭제
    @DeleteMapping("/{policyId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId) {
        bonusPolicyService.delete(companyId, policyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "보너스 정책 삭제 성공"),
                HttpStatus.OK);
    }

    // 회사 전체 정책 이력 조회
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("X-User-CompanyId") UUID companyId) {
        List<BonusPolicyResDto> data = bonusPolicyService.listByCompany(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "보너스 정책 이력 조회 성공"),
                HttpStatus.OK);
    }

    // 활성 정책 조회 없으면 기본 비활성 정책 자동 생성
    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestHeader("X-User-CompanyId") UUID companyId) {
        BonusPolicyResDto data = bonusPolicyService.getOrCreateActive(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(data, "활성 보너스 정책 조회 성공"),
                HttpStatus.OK);
    }
}
