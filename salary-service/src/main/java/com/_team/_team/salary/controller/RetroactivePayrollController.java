package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.RetroactivePayrollReqDto;
import com._team._team.salary.dto.resdto.RetroactivePayrollResDto;
import com._team._team.salary.service.RetroactivePayrollService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 소급분 자동 재계산 컨트롤러
 *  preview 차액 미리보기
 *  apply 새 RETROACTIVE 타입 Payroll 발행 DRAFT 상태로 생성
 */
@RestController
@RequestMapping("/salary/payroll/retroactive")
public class RetroactivePayrollController {

    private final RetroactivePayrollService retroactivePayrollService;

    @Autowired
    public RetroactivePayrollController(RetroactivePayrollService retroactivePayrollService) {
        this.retroactivePayrollService = retroactivePayrollService;
    }

    // 차액 미리보기 DB 변경 없음
    @PostMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody RetroactivePayrollReqDto reqDto) {
        RetroactivePayrollResDto data = retroactivePayrollService.preview(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "소급분 차액 미리보기 성공"),
                HttpStatus.OK);
    }

    // 발행 - 새 RETROACTIVE 타입 Payroll DRAFT 로 생성
    @PostMapping("/apply")
    public ResponseEntity<?> apply(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody RetroactivePayrollReqDto reqDto) {
        RetroactivePayrollResDto data = retroactivePayrollService.apply(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "소급분 명세서 발행 성공"),
                HttpStatus.CREATED);
    }
}
