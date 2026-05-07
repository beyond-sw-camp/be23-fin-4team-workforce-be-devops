package com._team._team.salary.controller;


import com._team._team.dto.ApiResponse;
import com._team._team.salary.dto.reqdto.PayGradeTableBulkCreateReqDto;
import com._team._team.salary.dto.reqdto.PayGradeTableCreateReqDto;
import com._team._team.salary.dto.reqdto.PayGradeTableUpdateReqDto;
import com._team._team.salary.dto.resdto.PayGradeTableResDto;
import com._team._team.salary.service.PayGradeTableService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/salary/pay-grade-table")
public class PayGradeTableController {

    private final PayGradeTableService service;

    @Autowired
    public PayGradeTableController(PayGradeTableService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody PayGradeTableCreateReqDto reqDto) {
        PayGradeTableResDto res = PayGradeTableResDto.fromEntity(
                service.create(companyId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(res, "호봉이 등록되었습니다."),
                HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<PayGradeTableResDto> list = service.findAllByCompany(companyId)
                .stream()
                .map(PayGradeTableResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(list, "호봉표 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/{payGradeTableId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payGradeTableId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        PayGradeTableResDto.fromEntity(service.findById(companyId, payGradeTableId)),
                        "호봉 조회 성공"),
                HttpStatus.OK);
    }

    @PutMapping("/{payGradeTableId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payGradeTableId,
            @Valid @RequestBody PayGradeTableUpdateReqDto reqDto) {
        PayGradeTableResDto res = PayGradeTableResDto.fromEntity(
                service.update(companyId, payGradeTableId, reqDto));
        return new ResponseEntity<>(
                ApiResponse.success(res, "호봉이 수정되었습니다."),
                HttpStatus.OK);
    }

    @DeleteMapping("/{payGradeTableId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payGradeTableId) {
        service.delete(companyId, payGradeTableId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "호봉이 삭제되었습니다."),
                HttpStatus.OK);
    }

    @PostMapping("/bulk-create")
    public ResponseEntity<?> bulkCreate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody PayGradeTableBulkCreateReqDto reqDto) {
        PayGradeTableService.BulkResult result = service.bulkCreate(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(result,
                        "신규 " + result.created() + "건, 교체 " + result.replaced() + "건 반영"),
                HttpStatus.OK);
    }
    // 내부 통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findAllInternal(@RequestParam UUID companyId) {
        List<PayGradeTableResDto> list = service.findAllByCompany(companyId)
                .stream()
                .map(PayGradeTableResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(list, "호봉표 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}