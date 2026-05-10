package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 인사발령 적용 시 salary-service 동기화 전용
 */
@RestController
@RequestMapping("/salary/internal")
public class SalaryPersonnelOrderInternalController {

    private final SalaryService salaryService;

    @Autowired
    public SalaryPersonnelOrderInternalController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    /**
     * 인사발령 적용 시 활성 Salary 직급/직책 동기화
     */
    @PostMapping("/apply-personnel-order")
    public ResponseEntity<?> applyPersonnelOrder(
            @RequestParam("memberId") UUID memberId,
            @RequestParam("companyId") UUID companyId,
            @RequestParam(value = "newJobGradeName", required = false) String newJobGradeName,
            @RequestParam(value = "newJobTitleName", required = false) String newJobTitleName,
            @RequestParam(value = "newStep", required = false) Integer newStep) {
        salaryService.applyPersonnelOrder(companyId, memberId, newJobGradeName, newJobTitleName, newStep);
        return new ResponseEntity<>(ApiResponse.success(null, "동기화 완료"), HttpStatus.OK);
    }
}
