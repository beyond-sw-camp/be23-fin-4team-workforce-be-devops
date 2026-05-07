package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.SalaryPolicyCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryPolicyUpdateReqDto;
import com._team._team.salary.service.SalaryPolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/salary/salary-policies")
public class SalaryPolicyController {

    private final SalaryPolicyService salaryService;

    @Autowired
    public SalaryPolicyController(SalaryPolicyService salaryService) {
        this.salaryService = salaryService;
    }

    /** 급여 정책 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody SalaryPolicyCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.save(companyId, reqDto), "급여 정책이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 급여 정책 단건 조회 */
    @GetMapping("/{salaryPolicyId}")
    public ResponseEntity<?> findPolicy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryPolicyId){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findById(companyId, salaryPolicyId), "급여 정책 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 회사별 급여 정책 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findByCompanyId(
            @RequestHeader("X-User-CompanyId") UUID companyId){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findByCompanyId(companyId), "급여 정책 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여 정책 수정 */
    @PutMapping("/{salaryPolicyId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryPolicyId,
            @Valid @RequestBody SalaryPolicyUpdateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.update(companyId, salaryPolicyId, reqDto), "급여 정책이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여 정책 삭제 */
    @DeleteMapping("/{salaryPolicyId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryPolicyId,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        salaryService.delete(companyId, salaryPolicyId, force);
        return new ResponseEntity<>(
                ApiResponse.success(null, "급여 정책이 삭제되었습니다."),
                HttpStatus.OK
        );
    }
    // 내부 통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findByCompanyIdInternal(@RequestParam UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findByCompanyId(companyId),
                        "급여 정책 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
