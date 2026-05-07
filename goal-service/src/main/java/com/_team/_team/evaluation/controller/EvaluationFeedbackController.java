package com._team._team.evaluation.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.domain.EvaluationFeedback;
import com._team._team.evaluation.repository.EvaluationFeedbackRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * EvaluationFeedbackController (v1: read-only 의의제기).
 *
 *  본인 결과에 대한 단순 코멘트만 등록. 시즌 admin 만 조회.
 *  v2 에서 정식 재심 워크플로우로 확장.
 */
@RestController
@RequestMapping("/evaluation/responses/{responseId}/feedback")
public class EvaluationFeedbackController {

    private final EvaluationFeedbackRepository feedbackRepository;

    public EvaluationFeedbackController(EvaluationFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @PostMapping
    public ApiResponse<EvaluationFeedback> create(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestBody FeedbackCreateReq req) {
        EvaluationFeedback f = EvaluationFeedback.builder()
                .responseId(responseId)
                .memberId(UUID.fromString(memberId))
                .content(req.getContent())
                .build();
        return ApiResponse.success(feedbackRepository.save(f), "피드백이 등록되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<EvaluationFeedback>> list(@PathVariable UUID responseId) {
        return ApiResponse.success(
                feedbackRepository.findByResponseIdOrderByCreatedAtAsc(responseId),
                "피드백 목록"
        );
    }

    @Data
    public static class FeedbackCreateReq {
        @NotBlank
        @Size(max = 5000)
        private String content;
    }
}
