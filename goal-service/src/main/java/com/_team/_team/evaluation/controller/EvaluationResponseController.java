package com._team._team.evaluation.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.dto.reqdto.LeadReassignReqDto;
import com._team._team.evaluation.dto.reqdto.ObjectionReqDto;
import com._team._team.evaluation.dto.reqdto.ObjectionResolveReqDto;
import com._team._team.evaluation.dto.reqdto.ReasonReqDto;
import com._team._team.evaluation.dto.reqdto.SelfAnswersReqDto;
import com._team._team.evaluation.dto.resdto.EvaluationResponseResDto;
import com._team._team.evaluation.service.EvaluationResponseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/evaluation/responses")
public class EvaluationResponseController {

    private final EvaluationResponseService responseService;

    public EvaluationResponseController(EvaluationResponseService responseService) {
        this.responseService = responseService;
    }

    @GetMapping("/me/self")
    public ApiResponse<List<EvaluationResponseResDto>> mySelfList(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                responseService.listMine(UUID.fromString(memberId), UUID.fromString(companyId)),
                "My self-evaluation responses loaded successfully."
        );
    }

    @GetMapping("/me/received")
    public ApiResponse<List<EvaluationResponseResDto>> myReceivedList(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                responseService.listMyReceived(UUID.fromString(memberId), UUID.fromString(companyId)),
                "Published evaluation results loaded successfully."
        );
    }

    @GetMapping("/me/evaluator-assignments")
    public ApiResponse<List<EvaluationResponseResDto>> myEvaluatorAssignments(
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        return ApiResponse.success(
                responseService.listMyEvaluatorAssignments(UUID.fromString(memberId), UUID.fromString(companyId)),
                "Evaluator assignments loaded successfully."
        );
    }

    @PatchMapping("/{responseId}/self/save")
    public ApiResponse<EvaluationResponseResDto> saveSelf(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestBody @Valid SelfAnswersReqDto dto) {
        return ApiResponse.success(
                responseService.saveSelf(responseId, UUID.fromString(memberId), dto),
                "Self-evaluation draft saved successfully."
        );
    }

    @GetMapping("/{responseId}/as-evaluator")
    public ApiResponse<EvaluationResponseResDto> viewAsEvaluator(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String requesterId) {
        return ApiResponse.success(
                responseService.viewAsEvaluator(responseId, UUID.fromString(requesterId)),
                "Evaluation response loaded for evaluator."
        );
    }

    @PostMapping("/{responseId}/self/submit")
    public ApiResponse<EvaluationResponseResDto> submitSelf(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestBody @Valid SelfAnswersReqDto dto) {
        return ApiResponse.success(
                responseService.submitSelf(responseId, UUID.fromString(memberId), dto),
                "Self-evaluation submitted successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{responseId}/lead")
    public ApiResponse<EvaluationResponseResDto> reassignLead(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String actorId,
            @RequestBody @Valid LeadReassignReqDto dto) {
        return ApiResponse.success(
                responseService.reassignLead(
                        responseId,
                        dto.getEvaluatorId(),
                        UUID.fromString(actorId),
                        dto.getReason()
                ),
                "Lead evaluator reassigned successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{responseId}/skip-leaver")
    public ApiResponse<EvaluationResponseResDto> skipLeaver(
            @PathVariable UUID responseId,
            @RequestBody(required = false) ReasonReqDto dto) {
        return ApiResponse.success(
                responseService.skipLeaver(responseId, dto != null ? dto.getReason() : null),
                "Response marked as skipped leaver successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{responseId}/reopen")
    public ApiResponse<EvaluationResponseResDto> reopenForCorrection(
            @PathVariable UUID responseId,
            @RequestBody(required = false) ReasonReqDto dto) {
        return ApiResponse.success(
                responseService.reopenForCorrection(responseId, dto != null ? dto.getReason() : null),
                "Evaluation response reopened successfully."
        );
    }

    @PostMapping("/{responseId}/objection")
    public ApiResponse<EvaluationResponseResDto> requestObjection(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestBody(required = false) ObjectionReqDto dto) {
        return ApiResponse.success(
                responseService.requestObjection(
                        responseId,
                        UUID.fromString(memberId),
                        dto != null ? dto.getMessage() : null
                ),
                "Objection requested successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{responseId}/objection/review")
    public ApiResponse<EvaluationResponseResDto> reviewObjection(@PathVariable UUID responseId) {
        return ApiResponse.success(
                responseService.reviewObjection(responseId),
                "Objection moved to reviewing successfully."
        );
    }

    @CheckPermission(resource = Resource.EVALUATION, action = Action.UPDATE)
    @PostMapping("/{responseId}/objection/resolve")
    public ApiResponse<EvaluationResponseResDto> resolveObjection(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String resolverId,
            @RequestBody(required = false) ObjectionResolveReqDto dto) {
        return ApiResponse.success(
                responseService.resolveObjection(
                        responseId,
                        UUID.fromString(resolverId),
                        dto != null ? dto.getResolution() : null
                ),
                "Objection resolved successfully."
        );
    }
}
