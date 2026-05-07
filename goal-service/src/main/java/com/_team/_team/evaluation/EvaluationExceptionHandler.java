package com._team._team.evaluation;

import com._team._team.dto.ApiResponse;
import com._team._team.evaluation.dto.resdto.SeasonActivationBlockedBody;
import com._team._team.evaluation.exception.SeasonActivationBlockedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 평가 도메인 예외 — {@link SeasonActivationBlockedException} 은 payload(멤버 목록)를 data 에 실어 422 로 반환.
 * {@code CommonExceptionHandler} 의 {@code BusinessException} 처리보다 우선한다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EvaluationExceptionHandler {

    @ExceptionHandler(SeasonActivationBlockedException.class)
    public ResponseEntity<ApiResponse<SeasonActivationBlockedBody>> seasonActivationBlocked(
            SeasonActivationBlockedException e) {
        SeasonActivationBlockedBody body = SeasonActivationBlockedBody.builder()
                .weightShortageMembers(toStrList(e.getWeightShortageMembers()))
                .pendingBundleMembers(toStrList(e.getPendingBundleMembers()))
                .missingGoalsMembers(toStrList(e.getMissingGoalsMembers()))
                .build();
        // Maven `workforce-common` JAR 에 `ApiResponse.fail(msg, data)` 가 없을 수 있어 생성자 사용
        return ResponseEntity
                .status(e.getStatus())
                .body(new ApiResponse<SeasonActivationBlockedBody>(false, e.getMessage(), body));
    }

    private static List<String> toStrList(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(UUID::toString).collect(Collectors.toList());
    }
}
