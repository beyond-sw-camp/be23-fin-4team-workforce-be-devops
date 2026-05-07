package com._team._team.evaluation.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.dto.reqdto.CalibrationUpsertReqDto;
import com._team._team.evaluation.dto.reqdto.ConfirmReqDto;
import com._team._team.evaluation.dto.resdto.CalibrationResDto;
import com._team._team.evaluation.dto.resdto.EvaluationResponseResDto;
import com._team._team.evaluation.service.EvaluationCalibrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * EvaluationCalibrationController — Lead/Assistant 등급 조정 + 확정.
 */
@RestController
@RequestMapping("/evaluation/responses/{responseId}")
public class EvaluationCalibrationController {

    private final EvaluationCalibrationService calibrationService;

    public EvaluationCalibrationController(EvaluationCalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    @GetMapping("/calibrations")
    public ApiResponse<List<CalibrationResDto>> list(
            @PathVariable UUID responseId) {
        return ApiResponse.success(calibrationService.list(responseId), "calibration 목록");
    }

    @PatchMapping("/calibrations")
    public ApiResponse<CalibrationResDto> upsert(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String evaluatorId,
            @RequestBody @Valid CalibrationUpsertReqDto dto) {
        return ApiResponse.success(
                calibrationService.upsert(responseId, UUID.fromString(evaluatorId), dto),
                "calibration 저장"
        );
    }

    @PostMapping("/confirm")
    public ApiResponse<EvaluationResponseResDto> confirm(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String actorId,
            @RequestBody @Valid ConfirmReqDto dto) {
        return ApiResponse.success(
                calibrationService.confirm(responseId, UUID.fromString(actorId), dto),
                "확정되었습니다."
        );
    }

    @PostMapping("/unconfirm")
    public ApiResponse<EvaluationResponseResDto> unconfirm(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String actorId) {
        return ApiResponse.success(
                calibrationService.unconfirm(responseId, UUID.fromString(actorId)),
                "확정이 취소되었습니다."
        );
    }
}
