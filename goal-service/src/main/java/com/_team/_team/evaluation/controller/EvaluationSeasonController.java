package com._team._team.evaluation.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.dto.reqdto.SeasonCreateReqDto;
import com._team._team.evaluation.dto.reqdto.SeasonUpdateReqDto;
import com._team._team.evaluation.dto.resdto.SeasonOperationalAlertsResDto;
import com._team._team.evaluation.dto.resdto.PublishBlockersResDto;
import com._team._team.evaluation.dto.resdto.EvaluationResponseResDto;
import com._team._team.evaluation.service.EvaluationAccessScopeService;
import com._team._team.evaluation.service.EvaluationGroupTranslator;
import com._team._team.evaluation.service.EvaluationResponseService;
import com._team._team.evaluation.service.EvaluationSeasonService;
import com._team._team.evaluation.service.SeasonActivationService;
import com._team._team.meeting.dto.resdto.MeetingRecordResDto;
import com._team._team.meeting.service.MeetingRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/evaluation/seasons")
public class EvaluationSeasonController {

    private final EvaluationSeasonService seasonService;
    private final EvaluationGroupTranslator groupTranslator;
    private final MeetingRecordService meetingRecordService;
    private final EvaluationResponseService responseService;
    private final EvaluationAccessScopeService accessScopeService;

    public EvaluationSeasonController(EvaluationSeasonService seasonService,
                                      EvaluationGroupTranslator groupTranslator,
                                      MeetingRecordService meetingRecordService,
                                      EvaluationResponseService responseService,
                                      EvaluationAccessScopeService accessScopeService) {
        this.seasonService = seasonService;
        this.groupTranslator = groupTranslator;
        this.meetingRecordService = meetingRecordService;
        this.responseService = responseService;
        this.accessScopeService = accessScopeService;
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.CREATE)
    @PostMapping
    public ApiResponse<EvaluationSeason> create(
            @RequestBody SeasonCreateReqDto dto,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                seasonService.create(UUID.fromString(companyId), dto),
                "Evaluation season created successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping
    public ApiResponse<List<EvaluationSeason>> list(
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                seasonService.list(UUID.fromString(companyId)),
                "Evaluation seasons loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}")
    public ApiResponse<EvaluationSeason> get(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                seasonService.get(seasonId, UUID.fromString(companyId)),
                "Evaluation season loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PatchMapping("/{seasonId}")
    public ApiResponse<EvaluationSeason> update(
            @PathVariable UUID seasonId,
            @RequestBody SeasonUpdateReqDto dto,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                seasonService.update(seasonId, UUID.fromString(companyId), dto),
                "Evaluation season updated successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.DELETE)
    @DeleteMapping("/{seasonId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.delete(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Evaluation season deleted successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/open-self-eval")
    public ApiResponse<Void> openSelfEval(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        List<SeasonActivationService.TargetSpec> targetSpecs = groupTranslator.translate(seasonId);
        seasonService.openSelfEval(seasonId, UUID.fromString(companyId), targetSpecs);
        return ApiResponse.success(null, "Self evaluation opened successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/open-manager-eval")
    public ApiResponse<Void> openManagerEval(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.advanceToManagerEval(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Manager evaluation opened successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/open-grade-confirm")
    public ApiResponse<Void> openGradeConfirm(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.advanceToGradeConfirm(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Grade confirmation opened successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/publish-results")
    public ApiResponse<Void> publish(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.publishResults(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Evaluation results published successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/open-interview")
    public ApiResponse<Void> openInterview(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.openInterview(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Interview stage opened successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/close")
    public ApiResponse<Void> close(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        seasonService.close(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Evaluation season closed successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}/publish-blockers")
    public ApiResponse<PublishBlockersResDto> publishBlockers(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        return ApiResponse.success(
                seasonService.getPublishBlockers(seasonId, UUID.fromString(companyId), scope),
                "Publish blockers loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}/operational-alerts")
    public ApiResponse<SeasonOperationalAlertsResDto> operationalAlerts(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        return ApiResponse.success(
                seasonService.getOperationalAlerts(seasonId, UUID.fromString(companyId), scope),
                "Operational alerts loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{seasonId}/meetings/regenerate")
    public ApiResponse<Void> regenerateMeetings(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        meetingRecordService.regenerateFeedbackMeetingsForSeason(seasonId, UUID.fromString(companyId));
        return ApiResponse.success(null, "Feedback meetings regenerated successfully.");
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}/meetings/status")
    public ApiResponse<Map<String, Integer>> meetingStatus(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        return ApiResponse.success(
                meetingRecordService.getSeasonStatus(seasonId, UUID.fromString(companyId), scope),
                "Meeting status loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}/responses")
    public ApiResponse<List<EvaluationResponseResDto>> responses(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        return ApiResponse.success(
                responseService.listSeasonResponses(seasonId, UUID.fromString(companyId), scope),
                "Season evaluation responses loaded successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.READ)
    @GetMapping("/{seasonId}/meetings")
    public ApiResponse<List<MeetingRecordResDto>> meetings(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestHeader("X-User-UUID") String requesterId,
            HttpServletRequest request) {
        EvaluationAccessScopeService.AccessScope scope = accessScopeService.resolveOperationReadScope(
                request,
                UUID.fromString(companyId),
                UUID.fromString(requesterId)
        );
        return ApiResponse.success(
                meetingRecordService.listBySeason(seasonId, UUID.fromString(companyId), scope),
                "Season meetings loaded successfully."
        );
    }
}
