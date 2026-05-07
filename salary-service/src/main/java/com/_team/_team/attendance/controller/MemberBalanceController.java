package com._team._team.attendance.controller;

import com._team._team.attendance.service.MemberBalanceService;
import com._team._team.attendance.dto.reqDto.MemberBalanceCreateReqDto;
import com._team._team.attendance.dto.resDto.MemberBalanceResDto;
import com._team._team.attendance.dto.reqDto.MemberBalanceUseReqDto;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


/**
 * 휴가 잔여 Controller
 *
 * [관리자용]
 * POST /member-balance/grant         사원에게 휴가 부여
 *
 * [사원용]
 * GET  /member-balance               내 휴가 잔여 조회
 *
 * [시스템 연동] approval-service → Kafka
 * POST /member-balance/use           휴가 차감 (승인 시)
 * POST /member-balance/restore       휴가 복구 (취소/반려 시)
 */
@RestController
@RequestMapping("/member-balance")
public class MemberBalanceController {

    private final MemberBalanceService memberBalanceService;

    @Autowired
    public MemberBalanceController(MemberBalanceService memberBalanceService) {
        this.memberBalanceService = memberBalanceService;
    }

    // 휴가 부여 (관리자용)
    @PostMapping("/grant")
    public ResponseEntity<?> grantBalance(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody MemberBalanceCreateReqDto reqDto){
        MemberBalanceResDto resDto = memberBalanceService.grantBalance(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴가가 부여되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 내 휴가 잔여 조회 (사원용)
    @GetMapping
    public ResponseEntity<?> findBalances(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        List<MemberBalanceResDto> resDtoList = memberBalanceService.findBalances(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "휴가 잔여 조회 성공"),
                HttpStatus.OK
        );
    }

    // 휴가 차감 (approval-service 연동) - 내부 시스템 API
    @PostMapping("/use")
    public ResponseEntity<?> useBalance(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody MemberBalanceUseReqDto reqDto) {
        MemberBalanceResDto resDto = memberBalanceService.useBalance(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴가가 차감되었습니다."),
                HttpStatus.OK
        );
    }

    // 휴가 복구 (취소/반려 시), 승인 취소 or 관리자 반려 시 호출 - 내부 시스템 API
    @PostMapping("/restore")
    public ResponseEntity<?> restoreBalance(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody MemberBalanceUseReqDto reqDto) {
        MemberBalanceResDto resDto = memberBalanceService.restoreBalance(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "휴가가 복구되었습니다."),
                HttpStatus.OK
        );
    }

    // 이월 동의 회신 (사원용) - 회사 정책 isCarryoverConsentYn='Y' 인 회사에서 본인 잔고에 대해 호출
    @PostMapping("/{memberBalanceId}/carryover-consent")
    public ResponseEntity<?> agreeCarryover(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID memberBalanceId) {
        MemberBalanceResDto resDto = memberBalanceService.agreeCarryover(companyId, memberId, memberBalanceId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "이월 동의가 접수되었습니다."),
                HttpStatus.OK
        );
    }

    // 이월 동의 철회 (사원용)
    @DeleteMapping("/{memberBalanceId}/carryover-consent")
    public ResponseEntity<?> revokeCarryoverConsent(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID memberBalanceId) {
        MemberBalanceResDto resDto = memberBalanceService.revokeCarryoverConsent(companyId, memberId, memberBalanceId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "이월 동의가 철회되었습니다."),
                HttpStatus.OK
        );
    }
}
