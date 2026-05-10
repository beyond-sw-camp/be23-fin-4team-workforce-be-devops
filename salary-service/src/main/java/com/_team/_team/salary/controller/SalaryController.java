package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.SalaryService;
import com._team._team.salary.dto.reqdto.SalaryCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryUpdateReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/salary/salaries")
public class SalaryController {

    private final SalaryService salaryService;

    @Autowired
    public SalaryController(SalaryService salaryService){
        this.salaryService = salaryService;
    }

    /** 급여 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody SalaryCreateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.createSalary(companyId, reqDto), "급여가 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 급여 단건 조회 */
    @GetMapping("/{salaryId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryId){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findById(companyId, salaryId), "급여 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 회사별 급여 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findByCompanyId(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findByCompanyId(companyId), "급여 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 급여대장 생성 직전 사전 검증 , 활성 Salary 미등록 / 계좌 미등록 직원 카운트, 목록 반환
     */
    @GetMapping("/precheck")
    public ResponseEntity<?> precheckPayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.precheckPayroll(companyId), "급여대장 사전 검증 완료"),
                HttpStatus.OK
        );
    }

    /** 직원별 급여 이력 조회 */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<?> findByMemberId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberId){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.findByMemberId(companyId, memberId), "직원별 급여 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여 수정 */
    @PutMapping("/{salaryId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryId,
            @Valid @RequestBody SalaryUpdateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(salaryService.update(companyId, salaryId, reqDto), "급여가 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여 삭제 - force=true 면 현재 적용 중이어도 강제 삭제 (잘못 등록한 행 정리용) */
    @DeleteMapping("/{salaryId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID salaryId,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force){
        salaryService.delete(companyId, salaryId, force);
        return new ResponseEntity<>(
                ApiResponse.success(null, "급여가 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    /** 본인 급여 변동 이력 조회 (직원 마이페이지용) */
    @GetMapping("/my")
    public ResponseEntity<?> findMyHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        salaryService.findMyHistory(companyId, memberId),
                        "내 급여 이력 조회 성공"),
                HttpStatus.OK
        );
    }
}
