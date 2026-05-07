package com._team._team.attendance.controller;

import com._team._team.attendance.service.LeavePolicyService;
import com._team._team.attendance.dto.reqDto.LeavePolicyCreateReqDto;
import com._team._team.attendance.dto.reqDto.LeavePolicyUpdateReqDto;
import com._team._team.attendance.dto.resDto.LeavePolicyResDto;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/leave-policies")
public class LeavePolicyController {
    private final LeavePolicyService leavePolicyService;

    @Autowired
    public LeavePolicyController(LeavePolicyService leavePolicyService) {
        this.leavePolicyService = leavePolicyService;
    }

    /** 연차 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> createPolicy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody LeavePolicyCreateReqDto reqDto){
        LeavePolicyResDto resDto = leavePolicyService.createPolicy(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto ,"연차 정책이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 연차 정책 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findPolicies(
            @RequestHeader("X-User-CompanyId") UUID companyId){
        List<LeavePolicyResDto> resDtoList = leavePolicyService.findPolicies(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "연차 정책 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 연차 정책 단건 조회 */
    @GetMapping("/{policyId}")
    public ResponseEntity<?> findPolicy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId){
       LeavePolicyResDto resDto = leavePolicyService.findPolicy(companyId, policyId);
       return new ResponseEntity<>(
               ApiResponse.success(resDto, "연차 정책 조회 성공"),
               HttpStatus.OK
       );
    }

    /** 연차 정책 수정 */
    @PutMapping("/{policyId}")
    public ResponseEntity<?> updatePolicy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId,
            @Valid @RequestBody LeavePolicyUpdateReqDto reqDto){
        LeavePolicyResDto resDto = leavePolicyService.updatePolicy(companyId, policyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto,"연차 정책이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 연차 정책 삭제 */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<?> deletePolicy(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID policyId){
        leavePolicyService.deletePolicy(companyId, policyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "연차 정책이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    /** ai-service 서비스 간 내부 호출용 - 연차 정책 목록 조회 */
    @GetMapping("/internal")
    public ResponseEntity<?> findPoliciesInternal(@RequestParam UUID companyId) {
        List<LeavePolicyResDto> resDtoList = leavePolicyService.findPolicies(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "연차 정책 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
