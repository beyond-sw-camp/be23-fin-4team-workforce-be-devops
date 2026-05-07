package com._team._team.contract.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.contract.dto.reqdto.ContractTemplateCreateReqDto;
import com._team._team.contract.dto.reqdto.ContractTemplateUpdateReqDto;
import com._team._team.contract.dto.resdto.ContractTemplateResDto;
import com._team._team.contract.service.ContractTemplateService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contract/templates")
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    @Autowired
    public ContractTemplateController(ContractTemplateService templateService) {
        this.templateService = templateService;
    }

    // 템플릿 생성
    @CheckPermission(resource = Resource.CONTRACT, action = Action.CREATE)
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody ContractTemplateCreateReqDto reqDto) {
        ContractTemplateResDto resDto = templateService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서 템플릿이 생성되었습니다."),
                HttpStatus.CREATED);
    }

    // 템플릿 수정
    @CheckPermission(resource = Resource.CONTRACT, action = Action.UPDATE)
    @PutMapping("/{templateId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID templateId,
            @Valid @RequestBody ContractTemplateUpdateReqDto reqDto) {
        ContractTemplateResDto resDto = templateService.update(companyId, templateId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서 템플릿이 수정되었습니다."),
                HttpStatus.OK);
    }

    // 템플릿 단건 조회
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping("/{templateId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID templateId) {
        ContractTemplateResDto resDto = templateService.findById(companyId, templateId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서 템플릿 조회 성공"),
                HttpStatus.OK);
    }

    // 전체 템플릿 목록 (관리자용)
    @CheckPermission(resource = Resource.CONTRACT, action = Action.READ)
    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ContractTemplateResDto> result = templateService.findAll(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "전체 계약서 템플릿 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 활성 템플릿 목록
    @GetMapping("/active")
    public ResponseEntity<?> findActive(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<ContractTemplateResDto> result = templateService.findActive(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "활성 계약서 템플릿 목록 조회 성공"),
                HttpStatus.OK);
    }

    // 활성화
    @CheckPermission(resource = Resource.CONTRACT, action = Action.UPDATE)
    @PatchMapping("/{templateId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID templateId) {
        ContractTemplateResDto resDto = templateService.activate(companyId, templateId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서 템플릿이 활성화되었습니다."),
                HttpStatus.OK);
    }

    // 비활성화
    @CheckPermission(resource = Resource.CONTRACT, action = Action.UPDATE)
    @PatchMapping("/{templateId}/deactivate")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID templateId) {
        ContractTemplateResDto resDto = templateService.deactivate(companyId, templateId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "계약서 템플릿이 비활성화되었습니다."),
                HttpStatus.OK);
    }

    // 기본 템플릿 자동 생성 (내부 호출용)
    @PostMapping("/init")
    public void initDefaultTemplates(@RequestParam UUID companyId) {
        templateService.initDefaultTemplates(companyId);
    }


}
