package com._team._team.approval.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.approval.dto.reqdto.AbsenceProxyCreateReqDto;
import com._team._team.approval.dto.resdto.AbsenceProxyResDto;
import com._team._team.approval.service.AbsenceProxyService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/absence-proxy")
public class AbsenceProxyController {

    private final AbsenceProxyService absenceProxyService;

    @Autowired
    public AbsenceProxyController(AbsenceProxyService absenceProxyService) {
        this.absenceProxyService = absenceProxyService;
    }

    // 부재 위임 등록
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody AbsenceProxyCreateReqDto reqDto) {
        AbsenceProxyResDto resDto = absenceProxyService.create(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "부재 위임이 등록되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 내가 등록한 위임 목록 (현재 + 미래)
    @GetMapping("/my")
    public ResponseEntity<?> findMyProxies(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        List<AbsenceProxyResDto> result = absenceProxyService.findMyProxies(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "내 위임 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 나에게 위임된 목록 (현재 + 미래)
    @GetMapping("/delegated")
    public ResponseEntity<?> findDelegatedToMe(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        List<AbsenceProxyResDto> result = absenceProxyService.findDelegatedToMe(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "위임받은 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 위임 비활성화 (취소)
    @PatchMapping("/{proxyId}/deactivate")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID proxyId) {
        AbsenceProxyResDto resDto = absenceProxyService.deactivate(companyId, memberId, proxyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "부재 위임이 취소되었습니다."),
                HttpStatus.OK
        );
    }
}
