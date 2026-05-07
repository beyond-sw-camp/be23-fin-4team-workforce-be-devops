package com._team._team.evaluation.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.dto.reqdto.EvaluatorMapAutoReqDto;
import com._team._team.evaluation.dto.reqdto.EvaluatorMapUpdateReqDto;
import com._team._team.evaluation.dto.reqdto.GroupCreateReqDto;
import com._team._team.evaluation.dto.reqdto.GroupUpdateReqDto;
import com._team._team.evaluation.dto.resdto.GroupResDto;
import com._team._team.evaluation.service.EvaluationAccessScopeService;
import com._team._team.evaluation.service.EvaluationGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/evaluation/seasons/{seasonId}/groups")
public class EvaluationGroupController {

    private final EvaluationGroupService groupService;
    private final EvaluationAccessScopeService accessScopeService;

    public EvaluationGroupController(EvaluationGroupService groupService,
                                     EvaluationAccessScopeService accessScopeService) {
        this.groupService = groupService;
        this.accessScopeService = accessScopeService;
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupResDto>>> listGroups(
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID seasonId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        List<GroupResDto> result = groupService.listGroups(seasonId, UUID.fromString(companyId), scope);
        return ResponseEntity.ok(ApiResponse.success(result, "평가 그룹 목록 조회 성공"));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.CREATE)
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResDto>> createGroup(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID seasonId,
            @Valid @RequestBody GroupCreateReqDto dto) {
        GroupResDto result = groupService.createGroup(seasonId, UUID.fromString(companyId), dto);
        return ResponseEntity.ok(ApiResponse.success(result, "평가 그룹이 생성되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PatchMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupResDto>> updateGroup(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID seasonId,
            @PathVariable UUID groupId,
            @RequestBody GroupUpdateReqDto dto) {
        GroupResDto result = groupService.updateGroup(seasonId, groupId, UUID.fromString(companyId), dto);
        return ResponseEntity.ok(ApiResponse.success(result, "평가 그룹이 수정되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.DELETE)
    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID groupId) {
        groupService.deleteGroup(groupId, UUID.fromString(companyId));
        return ResponseEntity.ok(ApiResponse.success(null, "평가 그룹이 삭제되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{groupId}/evaluator-maps/auto")
    public ResponseEntity<ApiResponse<GroupResDto>> autoAssignEvaluators(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID seasonId,
            @PathVariable UUID groupId,
            @Valid @RequestBody EvaluatorMapAutoReqDto dto) {
        GroupResDto result = groupService.autoAssignEvaluators(
                seasonId, groupId, UUID.fromString(companyId), dto.getBasis());
        return ResponseEntity.ok(ApiResponse.success(result, "평가자 자동 지정이 적용되었습니다."));
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PatchMapping("/{groupId}/evaluator-maps")
    public ResponseEntity<ApiResponse<GroupResDto>> updateEvaluatorMaps(
            @RequestHeader("X-User-CompanyId") String companyId,
            @PathVariable UUID groupId,
            @RequestBody EvaluatorMapUpdateReqDto dto) {
        GroupResDto result = groupService.updateEvaluatorMaps(groupId, UUID.fromString(companyId), dto.getEvaluatorMaps());
        return ResponseEntity.ok(ApiResponse.success(result, "평가자 매핑이 수정되었습니다."));
    }
}
