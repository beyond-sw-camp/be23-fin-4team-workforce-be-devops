package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.UnusedLeavePayoutService;
import com._team._team.salary.dto.reqdto.UnusedLeavePayoutApplyReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.UUID;


/**
 * 미사용 연차수당 수동 처리 API
 * HR 관리자 권한으로 호출 (미리보기 → 확정)
 */
@RestController
@RequestMapping("/salary/unused-leave")
public class UnusedLeavePayoutController {

    private final UnusedLeavePayoutService unusedLeavePayoutService;

    @Autowired
    public UnusedLeavePayoutController(UnusedLeavePayoutService unusedLeavePayoutService) {
        this.unusedLeavePayoutService = unusedLeavePayoutService;
    }

    /**
     * 미사용 수당 미리보기
     */
    @GetMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam int baseYear,
            @RequestParam String targetMonth) {
        YearMonth ym = YearMonth.parse(targetMonth);
        return new ResponseEntity<>(
                ApiResponse.success(unusedLeavePayoutService.preview(companyId, baseYear, ym),
                        "미사용 연차수당 계산 미리보기 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 미사용 수당 1월 급여대장 반영 (확정)
     */
    @PostMapping("/apply")
    public ResponseEntity<?> apply(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody UnusedLeavePayoutApplyReqDto reqDto) {
        unusedLeavePayoutService.apply(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "미사용 연차수당이 급여대장에 반영되었습니다."),
                HttpStatus.OK
        );
    }
}