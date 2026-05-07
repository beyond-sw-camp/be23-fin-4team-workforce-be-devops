package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.CompanyLeaveTypeCreateReqDto;
import com._team._team.attendance.dto.reqDto.CompanyLeaveTypeInitDefaultsReqDto;
import com._team._team.attendance.dto.reqDto.CompanyLeaveTypeUpdateReqDto;
import com._team._team.attendance.dto.resDto.CompanyLeaveTypeResDto;
import com._team._team.attendance.service.CompanyLeaveTypeService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attendance/leave-types")
public class CompanyLeaveTypeController {

    private final CompanyLeaveTypeService service;

    @Autowired
    public CompanyLeaveTypeController(CompanyLeaveTypeService service) {
        this.service = service;
    }

    // 휴가 생성
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody CompanyLeaveTypeCreateReqDto reqDto) {

        CompanyLeaveTypeResDto res = CompanyLeaveTypeResDto.fromEntity(
                service.create(companyId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(res, "휴가가 생성되었습니다."),
                HttpStatus.CREATED);
    }

    // 휴가 수정
    @PutMapping("/{companyLeaveTypeId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID companyLeaveTypeId,
            @Valid @RequestBody CompanyLeaveTypeUpdateReqDto reqDto) {

        CompanyLeaveTypeResDto res = CompanyLeaveTypeResDto.fromEntity(
                service.update(companyId, companyLeaveTypeId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(res, "휴가가 수정되었습니다."),
                HttpStatus.OK);
    }

    // 휴가 삭제 (연차 제외)
    @DeleteMapping("/{companyLeaveTypeId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID companyLeaveTypeId) {

        service.delete(companyId, companyLeaveTypeId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "휴가가 삭제되었습니다."),
                HttpStatus.OK);
    }

    // 목록 조회, 직원/관리자 공용 (휴가 신청 UI 드롭다운)
    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<CompanyLeaveTypeResDto> list = service.findAll(companyId).stream()
                .map(CompanyLeaveTypeResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(list, "휴가 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 휴가 단건 조회
    @GetMapping("/{companyLeaveTypeId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID companyLeaveTypeId) {

        CompanyLeaveTypeResDto res = CompanyLeaveTypeResDto.fromEntity(
                service.findById(companyId, companyLeaveTypeId));
        return new ResponseEntity<>(
                ApiResponse.success(res, "휴가 조회 성공"),
                HttpStatus.OK);
    }

    // 회사 가입 시 또는 [기본 휴가 불러오기] 시 호출
    @PostMapping("/init")
    public void initDefaults(
            @RequestParam UUID companyId,
            @RequestBody(required = false) CompanyLeaveTypeInitDefaultsReqDto req) {
        service.initializeDefaults(companyId, req != null ? req.getCodes() : null);
    }


    // ai-service 서비스 간 내부 호출용 - 회사 휴가 종류 전체 조회
    @GetMapping("/internal")
    public ResponseEntity<?> findAllInternal(@RequestParam UUID companyId) {
        List<CompanyLeaveTypeResDto> list = service.findAll(companyId).stream()
                .map(CompanyLeaveTypeResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(list, "휴가 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}