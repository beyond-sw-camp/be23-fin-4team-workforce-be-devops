package com._team._team.evaluation.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.dto.reqdto.DesignCreateReqDto;
import com._team._team.evaluation.dto.reqdto.DesignUpdateReqDto;
import com._team._team.evaluation.dto.resdto.DesignResDto;
import com._team._team.evaluation.service.EvaluationDesignService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/evaluation/designs")
public class EvaluationDesignController {

    private final EvaluationDesignService designService;

    public EvaluationDesignController(EvaluationDesignService designService) {
        this.designService = designService;
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping
    public ResponseEntity<ApiResponse<List<DesignResDto>>> listDesigns(
            @RequestHeader("X-User-CompanyId") String companyId) {
        List<DesignResDto> result = designService.listDesigns(UUID.fromString(companyId));
        return ResponseEntity.ok(ApiResponse.success(result, "평가 설계 목록 조회 성공"));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.CREATE)
    @PostMapping
    public ResponseEntity<ApiResponse<DesignResDto>> createDesign(
            @RequestHeader("X-User-CompanyId") String companyId,
            @Valid @RequestBody DesignCreateReqDto dto) {
        DesignResDto result = designService.createDesign(UUID.fromString(companyId), dto);
        return ResponseEntity.ok(ApiResponse.success(result, "평가 설계가 생성되었습니다."));
    }

    /**
     * 단건 설계 조회 — 목록과 달리 평가자 본인도 작성 화면에서 필요하므로 {@code @CheckPermission} 을 쓰지 않는다.
     * 서비스에서 동일 회사 소속 + 해당 설계를 쓰는 평가에 평가자로 배정된 경우에만 허용한다.
     */
    @GetMapping("/{designId}")
    public ResponseEntity<ApiResponse<DesignResDto>> getDesign(
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String memberId,
            @PathVariable UUID designId) {
        DesignResDto result = designService.getDesign(
                designId, UUID.fromString(companyId), UUID.fromString(memberId));
        return ResponseEntity.ok(ApiResponse.success(result, "평가 설계 조회 성공"));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PatchMapping("/{designId}")
    public ResponseEntity<ApiResponse<DesignResDto>> updateDesign(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID designId,
            @RequestBody DesignUpdateReqDto dto) {
        DesignResDto result = designService.updateDesign(designId, UUID.fromString(companyId), dto);
        return ResponseEntity.ok(ApiResponse.success(result, "평가 설계가 수정되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.CREATE)
    @PostMapping("/{designId}/duplicate")
    public ResponseEntity<ApiResponse<DesignResDto>> duplicateDesign(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID designId) {
        DesignResDto result = designService.duplicateDesign(designId, UUID.fromString(companyId));
        return ResponseEntity.ok(ApiResponse.success(result, "평가 설계가 복제되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.DELETE)
    @DeleteMapping("/{designId}")
    public ResponseEntity<ApiResponse<Void>> deleteDesign(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID designId) {
        designService.deleteDesign(designId, UUID.fromString(companyId));
        return ResponseEntity.ok(ApiResponse.success(null, "평가 설계가 삭제되었습니다."));
    }
}
