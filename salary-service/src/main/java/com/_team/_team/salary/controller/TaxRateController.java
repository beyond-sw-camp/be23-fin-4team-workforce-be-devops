package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.TaxRateService;
import com._team._team.salary.dto.reqdto.TaxRateCreateReqDto;
import com._team._team.salary.dto.reqdto.TaxRateUpdateReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/salary/taxRate")
public class TaxRateController {

    private final TaxRateService taxRateService;

    @Autowired
    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    /** 세율 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @Valid @RequestBody TaxRateCreateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.save(reqDto), "세율이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 세율 단건 조회 */
    @GetMapping("/{taxRateId}")
    public ResponseEntity<?> findById(
            @PathVariable UUID taxRateId){
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.findById(taxRateId), "세율 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 연도별 세율 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findByApplyYear(
            @RequestParam Integer applyYear){
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.findByApplyYear(applyYear), "연도별 세율 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 세율 수정 */
    @PutMapping("/{taxRateId}")
    public ResponseEntity<?> update(
            @PathVariable UUID taxRateId,
            @Valid @RequestBody TaxRateUpdateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(taxRateService.update(taxRateId, reqDto), "세율이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 세율 삭제 */
    @DeleteMapping("/{taxRateId}")
    public ResponseEntity<?> delete(
            @PathVariable UUID taxRateId){
        taxRateService.delete(taxRateId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "세율이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    // 지정 연도 표준 4대보험 + 세금 세율 시드, 기존 값은 보존
    @PostMapping("/init")
    public ResponseEntity<?> initializeDefaults(
            @RequestParam Integer applyYear) {
        TaxRateService.SeedResult result = taxRateService.initializeDefaults(applyYear);
        return new ResponseEntity<>(
                ApiResponse.success(result,
                        applyYear + "년 표준 세율 " + result.inserted() + "건 반영, "
                                + result.skipped() + "건 스킵"),
                HttpStatus.OK);
    }
}
