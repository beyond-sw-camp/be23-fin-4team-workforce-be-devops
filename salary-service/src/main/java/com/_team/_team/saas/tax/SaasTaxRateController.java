package com._team._team.saas.tax;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.TaxRateCreateReqDto;
import com._team._team.salary.dto.reqdto.TaxRateUpdateReqDto;
import com._team._team.salary.service.TaxRateService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SaaS 운영자용 4대보험/소득세 요율 관리
 * 매년 법령 변경에 따라 운영자가 등록/수정 (전 회사 공통 적용)
 */
@RestController
@RequestMapping("/saas/tax-rate")
public class SaasTaxRateController {

    private final TaxRateService taxRateService;

    @Autowired
    public SaasTaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    // 연도별 목록
    @GetMapping
    public ResponseEntity<?> list(@RequestParam Integer applyYear) {
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.findByApplyYear(applyYear), "연도별 세율 조회 성공"),
                HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody TaxRateCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.save(reqDto), "세율이 등록되었습니다."),
                HttpStatus.CREATED);
    }

    @PutMapping("/{taxRateId}")
    public ResponseEntity<?> update(
            @PathVariable UUID taxRateId,
            @Valid @RequestBody TaxRateUpdateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.update(taxRateId, reqDto), "세율이 수정되었습니다."),
                HttpStatus.OK);
    }

    @DeleteMapping("/{taxRateId}")
    public ResponseEntity<?> delete(@PathVariable UUID taxRateId) {
        taxRateService.delete(taxRateId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "세율이 삭제되었습니다."),
                HttpStatus.OK);
    }

    // 지정 연도 표준 4대보험 + 세금 시드 (이미 있는 유형은 skip - 멱등)
    @PostMapping("/init")
    public ResponseEntity<?> initializeDefaults(@RequestParam Integer applyYear) {
        TaxRateService.SeedResult result = taxRateService.initializeDefaults(applyYear);
        return new ResponseEntity<>(
                ApiResponse.success(result,
                        applyYear + "년 표준 세율 " + result.inserted() + "건 반영, " + result.skipped() + "건 스킵"),
                HttpStatus.OK);
    }
}
