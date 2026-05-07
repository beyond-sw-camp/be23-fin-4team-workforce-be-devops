package com._team._team.companybatch;

import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import com._team._team.saas.schedule.dto.SaasScheduleResDto;
import com._team._team.saas.schedule.dto.SaasScheduleUpdateReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 회사 관리자용 자동 작업 관리 API
@RestController
@RequestMapping("/salary/company-batch-schedule")
public class CompanyBatchScheduleController {

    private final CompanyBatchScheduleService service;

    @Autowired
    public CompanyBatchScheduleController(CompanyBatchScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Company-Id", required = false) String companyIdHeader) {
        UUID companyId = parseCompanyId(companyIdHeader);
        List<SaasScheduleResDto> result = service.listForCompany(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "회사별 자동 작업 목록 조회 성공"),
                HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<?> updateCron(
            @RequestHeader(value = "X-Company-Id", required = false) String companyIdHeader,
            @RequestParam String jobKey,
            @Valid @RequestBody SaasScheduleUpdateReqDto reqDto) {
        UUID companyId = parseCompanyId(companyIdHeader);
        service.updateCron(companyId, jobKey, reqDto.getCron());
        return new ResponseEntity<>(ApiResponse.success(null, "실행 시간 변경 성공"), HttpStatus.OK);
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(
            @RequestHeader(value = "X-Company-Id", required = false) String companyIdHeader,
            @RequestParam String jobKey) {
        UUID companyId = parseCompanyId(companyIdHeader);
        service.pause(companyId, jobKey);
        return new ResponseEntity<>(ApiResponse.success(null, "일시중지 됨"), HttpStatus.OK);
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(
            @RequestHeader(value = "X-Company-Id", required = false) String companyIdHeader,
            @RequestParam String jobKey) {
        UUID companyId = parseCompanyId(companyIdHeader);
        service.resume(companyId, jobKey);
        return new ResponseEntity<>(ApiResponse.success(null, "재개 됨"), HttpStatus.OK);
    }

    private UUID parseCompanyId(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "회사 식별 정보가 없습니다.");
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "회사 식별 형식이 잘못되었습니다.");
        }
    }
}
