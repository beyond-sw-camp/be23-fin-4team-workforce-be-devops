package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.BonusBatchReqDto;
import com._team._team.salary.dto.resdto.BonusBatchApplyResDto;
import com._team._team.salary.dto.resdto.BonusBatchPreviewResDto;
import com._team._team.salary.service.BonusBatchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 보너스 일괄 발행 - 시뮬 미리보기 + 발행
 */
@RestController
@RequestMapping("/salary/bonus")
public class BonusBatchController {

    private final BonusBatchService bonusBatchService;

    @Autowired
    public BonusBatchController(BonusBatchService bonusBatchService) {
        this.bonusBatchService = bonusBatchService;
    }

    /** 일괄 발행 시뮬 - 대상 직원 / 산출액 미리보기 (DB 변경 X) */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody BonusBatchReqDto reqDto) {
        BonusBatchPreviewResDto data = bonusBatchService.preview(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "보너스 발행 시뮬 성공"),
                HttpStatus.OK);
    }

    /** 일괄 발행  */
    @PostMapping("/apply")
    public ResponseEntity<?> apply(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody BonusBatchReqDto reqDto) {
        BonusBatchApplyResDto data = bonusBatchService.apply(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(data, "보너스 일괄 발행 완료"),
                HttpStatus.CREATED);
    }
}
