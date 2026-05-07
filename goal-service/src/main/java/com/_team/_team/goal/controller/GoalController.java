package com._team._team.goal.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import com._team._team.goal.dto.reqdto.GoalCreateReqDto;
import com._team._team.goal.dto.reqdto.GoalUpdateReqDto;
import com._team._team.goal.dto.resdto.GoalCycleResDto;
import com._team._team.goal.dto.resdto.GoalKrResDto;
import com._team._team.goal.dto.resdto.GoalObjectiveResDto;
import com._team._team.goal.dto.resdto.GoalViewResDto;
import com._team._team.goal.permission.PositionPermissionReader;
import com._team._team.goal.service.GoalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/goal")
public class GoalController {

    private final GoalService goalService;
    private final PositionPermissionReader positionPermissionReader;

    public GoalController(GoalService goalService,
                          PositionPermissionReader positionPermissionReader) {
        this.goalService = goalService;
        this.positionPermissionReader = positionPermissionReader;
    }

    @GetMapping("/me")
    public ApiResponse<List<GoalKrResDto>> myGoals(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam(required = false) GoalStatus status) {
        return ApiResponse.success(
                goalService.listMyGoals(UUID.fromString(memberId), UUID.fromString(companyId), status),
                "My goals loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.GOAL, action = Action.READ)
    @GetMapping("/company")
    public ApiResponse<List<GoalViewResDto>> companyGoals(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam(required = false) KpiCycle cycle,
            HttpServletRequest request) {
        boolean hasEvalRead = positionPermissionReader.hasPermission(request, Resource.EVALUATION, Action.READ);
        return ApiResponse.success(
                goalService.listCompanyGoals(
                        UUID.fromString(memberId),
                        UUID.fromString(companyId),
                        cycle,
                        hasEvalRead
                ),
                "Company goals loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/organization-cycles")
    public ApiResponse<List<GoalCycleResDto>> organizationGoalCycles(
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                goalService.listOrganizationGoalCycles(UUID.fromString(companyId)),
                "Organization goal cycles loaded successfully."
        );
    }

    @GetMapping("/organization")
    public ApiResponse<List<GoalKrResDto>> orgGoals(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam UUID orgId,
            HttpServletRequest request) {
        ensureCanReadOrganizationGoalScope(request);
        return ApiResponse.success(
                goalService.listOrgGoals(UUID.fromString(memberId), orgId, UUID.fromString(companyId)),
                "Organization member KRs loaded successfully."
        );
    }

    @GetMapping("/objectives/me")
    public ApiResponse<List<GoalObjectiveResDto>> myObjectives(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam(required = false) KpiCycle cycle) {
        return ApiResponse.success(
                goalService.listMyObjectives(UUID.fromString(memberId), UUID.fromString(companyId), cycle),
                "Managed organization objectives loaded successfully."
        );
    }

    @GetMapping("/objectives/organization")
    public ApiResponse<List<GoalObjectiveResDto>> organizationObjectives(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam UUID orgId,
            @RequestParam(required = false) KpiCycle cycle,
            HttpServletRequest request) {
        ensureCanReadOrganizationGoalScope(request);
        return ApiResponse.success(
                goalService.listOrgObjectives(UUID.fromString(memberId), orgId, UUID.fromString(companyId), cycle),
                "Organization objectives loaded successfully."
        );
    }

    @PostMapping
    public ApiResponse<GoalViewResDto> create(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestBody @Valid GoalCreateReqDto dto,
            HttpServletRequest request) {
        if (dto.getOwnerType() == GoalOwnerType.ORGANIZATION
                && !positionPermissionReader.canCreateOrganizationScopedGoal(request)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to create organization objectives."
            );
        }
        return ApiResponse.success(
                goalService.create(dto, UUID.fromString(memberId), UUID.fromString(companyId)),
                "Goal created successfully."
        );
    }

    @GetMapping("/{goalId}")
    public ApiResponse<GoalViewResDto> get(
            @PathVariable UUID goalId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            HttpServletRequest request) {
        boolean hasEvalRead = positionPermissionReader.hasPermission(request, Resource.EVALUATION, Action.READ);
        return ApiResponse.success(
                goalService.get(goalId, UUID.fromString(memberId), UUID.fromString(companyId), hasEvalRead),
                "Goal loaded successfully."
        );
    }

    @PatchMapping("/{goalId}")
    public ApiResponse<GoalViewResDto> update(
            @PathVariable UUID goalId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestBody @Valid GoalUpdateReqDto dto,
            HttpServletRequest request) {
        boolean canManageOrgGoal = positionPermissionReader.canCreateOrganizationScopedGoal(request);
        return ApiResponse.success(
                goalService.update(goalId, dto, UUID.fromString(memberId), UUID.fromString(companyId), canManageOrgGoal),
                "Goal updated successfully."
        );
    }

    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID goalId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            HttpServletRequest request) {
        boolean canManageOrgGoal = positionPermissionReader.canCreateOrganizationScopedGoal(request);
        goalService.delete(goalId, UUID.fromString(memberId), UUID.fromString(companyId), canManageOrgGoal);
        return ApiResponse.success(null, "Goal deleted successfully.");
    }

    @PostMapping("/{goalId}/cancel")
    public ApiResponse<GoalViewResDto> cancel(
            @PathVariable UUID goalId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            HttpServletRequest request) {
        boolean canManageOrgGoal = positionPermissionReader.canCreateOrganizationScopedGoal(request);
        return ApiResponse.success(
                goalService.cancelMine(goalId, UUID.fromString(memberId), UUID.fromString(companyId), canManageOrgGoal),
                "Goal cancelled successfully."
        );
    }

    private void ensureCanReadOrganizationGoalScope(HttpServletRequest request) {
        boolean canRead = positionPermissionReader.hasPermissionInAnyRange(
                request,
                Resource.GOAL,
                Action.READ,
                Set.of("TEAM", "COMPANY")
        );
        boolean canManage = positionPermissionReader.hasPermissionInAnyRange(
                request,
                Resource.GOAL,
                Action.UPDATE,
                Set.of("TEAM", "COMPANY")
        );
        if (!canRead && !canManage) {
            throw new com._team._team.dto.BusinessException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "You do not have permission to read organization goals."
            );
        }
    }

}
