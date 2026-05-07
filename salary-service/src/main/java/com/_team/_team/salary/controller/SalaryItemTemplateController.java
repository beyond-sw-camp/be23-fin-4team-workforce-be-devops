package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.SalaryItemTemplateService;
import com._team._team.salary.dto.reqdto.SalaryItemTemplateCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryItemTemplateUpdateReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 급여 항목 템플릿 컨트롤러
 */
@RestController
@RequestMapping("/salary/salary-item-templates")
public class SalaryItemTemplateController {

    private final SalaryItemTemplateService salaryItemTemplateService;

    @Autowired
    public SalaryItemTemplateController(SalaryItemTemplateService salaryItemTemplateService) {
        this.salaryItemTemplateService = salaryItemTemplateService;
    }

    /** 급여 항목 템플릿 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody SalaryItemTemplateCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryItemTemplateService.save(companyId, reqDto), "급여 항목 템플릿이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 급여 항목 템플릿 단건 조회 */
    @GetMapping("/{salaryItemTemplateId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryItemTemplateId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryItemTemplateService.findById(companyId, salaryItemTemplateId), "급여 항목 템플릿 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 회사별 급여 항목 템플릿 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findByCompanyId(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryItemTemplateService.findByCompanyId(companyId), "급여 항목 템플릿 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여 항목 템플릿 수정 */
    @PutMapping("/{salaryItemTemplateId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryItemTemplateId,
            @Valid @RequestBody SalaryItemTemplateUpdateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryItemTemplateService.update(companyId, salaryItemTemplateId, reqDto), "급여 항목 템플릿이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여 항목 템플릿 삭제 (soft delete) */
    @DeleteMapping("/{salaryItemTemplateId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryItemTemplateId) {
        salaryItemTemplateService.delete(companyId, salaryItemTemplateId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "급여 항목 템플릿이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    // 표준 템플릿 일괄 시드, 회사 온보딩 시 1회 호출
    @PostMapping("/init")
    public ResponseEntity<?> initializeDefaults(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        SalaryItemTemplateService.SeedResult result = salaryItemTemplateService.initializeDefaults(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, result.message()),
                HttpStatus.OK);
    }

    // 내부 통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findByCompanyIdInternal(@RequestParam UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryItemTemplateService.findByCompanyId(companyId),
                        "급여 항목 템플릿 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
