package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.OvertimePolicyCreateReqDto;
import com._team._team.attendance.dto.reqDto.OvertimePolicyUpdateReqDto;
import com._team._team.attendance.dto.resDto.OvertimePolicyResDto;
import com._team._team.attendance.service.OvertimePolicyService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


/**
 * 연장근로 정책 관리 (관리자 전용)
 */
@RestController
@RequestMapping("/attendance/overtime-policies")
public class OvertimePolicyController {
    private final OvertimePolicyService overtimePolicyService;

    @Autowired
    public OvertimePolicyController(OvertimePolicyService overtimePolicyService) {
        this.overtimePolicyService = overtimePolicyService;
    }

    /**
     * 정책 생성
     */
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody OvertimePolicyCreateReqDto reqDto) {

        OvertimePolicyResDto resDto = overtimePolicyService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 정책이 생성되었습니다."),
                HttpStatus.CREATED);
    }

    /**
     * 정책 수정
     */
    @PutMapping("/{policyId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId,
            @Valid @RequestBody OvertimePolicyUpdateReqDto reqDto) {

        OvertimePolicyResDto resDto =
                overtimePolicyService.update(policyId, companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 정책이 수정되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 현재 적용 중인 정책
     */
    @GetMapping("/current")
    public ResponseEntity<?> findCurrent(
            @RequestHeader("X-User-CompanyId") UUID companyId) {

        OvertimePolicyResDto resDto = overtimePolicyService.findCurrent(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "현재 연장근로 정책 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 단건 조회
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId) {

        OvertimePolicyResDto resDto = overtimePolicyService.findById(policyId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 정책 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 회사 정책 이력 전체
     */
    @GetMapping
    public ResponseEntity<?> findHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId) {

        List<OvertimePolicyResDto> resDto = overtimePolicyService.findHistory(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 정책 이력 조회 성공"),
                HttpStatus.OK);
    }
    // 내부통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findHistoryInternal(@RequestParam UUID companyId) {
        List<OvertimePolicyResDto> resDto = overtimePolicyService.findHistory(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "연장근로 정책 이력 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
