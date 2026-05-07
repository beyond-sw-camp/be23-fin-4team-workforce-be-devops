package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.dto.reqdto.SelfAnswersReqDto;
import com._team._team.evaluation.dto.resdto.EvaluationResponseResDto;
import com._team._team.evaluation.dto.resdto.ResponseResDto;
import com._team._team.evaluation.repository.EvaluationCalibrationRepository;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * EvaluationResponseService — 자기평가 단계 담당.
 *
 *  본인이 자기평가 입력 → 임시저장(saveSelf) / 제출(submitSelf).
 *  마감 후 자동 SUBMITTED(empty) 처리는 autoSubmitEmpty (Scheduler 가 호출).
 */
@Service
public class EvaluationResponseService {

    private final EvaluationResponseRepository responseRepository;
    private final EvaluationCalibrationRepository calibrationRepository;
    private final ObjectMapper objectMapper;

    public EvaluationResponseService(EvaluationResponseRepository responseRepository,
                                     EvaluationCalibrationRepository calibrationRepository,
                                     ObjectMapper objectMapper) {
        this.responseRepository = responseRepository;
        this.calibrationRepository = calibrationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<EvaluationResponseResDto> listMine(UUID memberId, UUID companyId) {
        return responseRepository
                .findByCompanyIdAndTargetMemberIdOrderByCreatedAtDesc(companyId, memberId)
                .stream().map(EvaluationResponseResDto::from).collect(Collectors.toList());
    }

    /**
     * 내가 LEAD/ASSISTANT 로 배정된 평가 응답 목록 (부하·동료 대상).
     * 평가 진행 화면에서 자기평가 목록과 별도로 노출하기 위함.
     */
    @Transactional
    public List<EvaluationResponseResDto> listMyEvaluatorAssignments(UUID memberId, UUID companyId) {
        List<EvaluationCalibration> mine = calibrationRepository.findByEvaluatorId(memberId);
        List<EvaluationResponseResDto> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (EvaluationCalibration cal : mine) {
            UUID rid = cal.getResponseId();
            if (!seen.add(rid)) {
                continue;
            }
            EvaluationResponse r = responseRepository.findById(rid).orElse(null);
            if (r == null || !companyId.equals(r.getCompanyId())) {
                continue;
            }
            if (r.getStage() == EvaluationStage.SKIPPED_LEAVER) {
                continue;
            }
            if (cal.getRole() == CalibrationRole.ASSISTANT) {
                out.add(EvaluationResponseResDto.fromForAssistant(r));
            } else {
                ensureLeadCalibrationExists(r);
                out.add(EvaluationResponseResDto.from(r));
            }
        }
        for (EvaluationResponse r : responseRepository.findByCompanyIdAndEvaluatorIdOrderByCreatedAtDesc(companyId, memberId)) {
            if (!seen.add(r.getResponseId())) {
                continue;
            }
            if (r.getStage() == EvaluationStage.SKIPPED_LEAVER) {
                continue;
            }
            ensureLeadCalibrationExists(r);
            out.add(EvaluationResponseResDto.from(r));
        }
        out.sort(Comparator.comparing(EvaluationResponseResDto::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /**
     * 내가 받은(평가 대상) 응답 중 "공개된 시즌의 CONFIRMED" 만 반환.
     *
     *  공개 게이트:
     *    1) stage == CONFIRMED (확정된 결과만)
     *    2) season.resultsPublishedAt != null  (공식 공개일이 지난 시즌만)
     *
     *  Lead 가 confirm 만 해도 공식 공개 전에는 직원이 결과를 미리 보지 못하도록 차단.
     *  공개 자체는 EvaluationSeasonService.publishResults() 가 수행하며,
     *  동일 트랜잭션에서 피드백 면담이 자동 생성됨 → 공개 시점 = 면담 예약 시점.
     */
    @Transactional(readOnly = true)
    public List<EvaluationResponseResDto> listMyReceived(UUID memberId, UUID companyId) {
        return responseRepository
                .findByCompanyIdAndTargetMemberIdOrderByCreatedAtDesc(companyId, memberId)
                .stream()
                .filter(r -> r.getStage() == EvaluationStage.CONFIRMED)
                .filter(r -> r.getGroup() != null
                          && r.getGroup().getSeason() != null
                          && r.getGroup().getSeason().getResultsPublishedAt() != null)
                .map(EvaluationResponseResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public EvaluationResponseResDto saveSelf(UUID responseId,
                                             UUID memberId,
                                             SelfAnswersReqDto dto) {
        EvaluationResponse r = mustGet(responseId);
        ensureSelf(r, memberId);
        ensureSelfEvalOpen(r);
        r.saveSelfAnswers(serialize(dto));
        return EvaluationResponseResDto.from(r);
    }

    @Transactional
    public EvaluationResponseResDto submitSelf(UUID responseId,
                                               UUID memberId,
                                               SelfAnswersReqDto dto) {
        EvaluationResponse r = mustGet(responseId);
        ensureSelf(r, memberId);
        ensureSelfEvalOpen(r);
        r.submitSelf(serialize(dto));
        return EvaluationResponseResDto.from(r);
    }

    /** 자기평가 마감 시점에 일괄 호출 — Scheduler 진입점 */
    @Transactional
    public int autoSubmitEmptyForCompany(UUID companyId) {
        List<EvaluationResponse> pending = responseRepository
                .findByCompanyIdAndStageOrderByCreatedAtDesc(companyId, EvaluationStage.SELF_PENDING);
        pending.forEach(EvaluationResponse::autoSubmitEmpty);
        return pending.size();
    }

    @Transactional(readOnly = true)
    public List<ResponseResDto> listMySeasonResult(UUID seasonId, UUID memberId, UUID companyId) {
        return responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .filter(r -> memberId.equals(r.getTargetMemberId()))
                .map(ResponseResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EvaluationResponseResDto> listSeasonResponses(UUID seasonId, UUID companyId) {
        return responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .sorted(Comparator.comparing(EvaluationResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(EvaluationResponseResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EvaluationResponseResDto> listSeasonResponses(
            UUID seasonId,
            UUID companyId,
            EvaluationAccessScopeService.AccessScope scope
    ) {
        return responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .filter(r -> canSeeInOperation(r, scope))
                .sorted(Comparator.comparing(EvaluationResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(EvaluationResponseResDto::from)
                .collect(Collectors.toList());
    }

    private boolean canSeeInOperation(EvaluationResponse response, EvaluationAccessScopeService.AccessScope scope) {
        if (scope == null || scope.companyWide()) {
            return true;
        }
        return scope.canSeeTarget(response.getTargetMemberId())
                || scope.isRequester(response.getEvaluatorId());
    }

    private EvaluationResponse mustGet(UUID responseId) {
        return responseRepository.findById(responseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "평가 응답을 찾을 수 없습니다."));
    }

    private void ensureSelf(EvaluationResponse r, UUID memberId) {
        if (!r.getTargetMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인의 자기평가만 입력 가능합니다.");
        }
    }

    private String serialize(SelfAnswersReqDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "자기평가 직렬화 실패");
        }
    }

    private void ensureSelfEvalOpen(EvaluationResponse r) {
        if (r.getGroup() == null || r.getGroup().getSeason() == null
                || !r.getGroup().getSeason().isSelfEvalEditable()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Self evaluation is not open.");
        }
    }

    private void ensureLeadCalibrationExists(EvaluationResponse response) {
        if (response.getEvaluatorId() == null) {
            return;
        }
        if (calibrationRepository.findByResponseIdAndRole(response.getResponseId(), CalibrationRole.LEAD).isPresent()) {
            return;
        }
        calibrationRepository.save(EvaluationCalibration.builder()
                .responseId(response.getResponseId())
                .evaluatorId(response.getEvaluatorId())
                .role(CalibrationRole.LEAD)
                .build());
    }

    // =================================================================
    //  v4 — 운영 시나리오 (HR / 본인)
    // =================================================================

    /**
     * v4 — 평가자(LEAD/ASSISTANT) 가 평가 대상 응답 조회.
     *  ASSISTANT 는 자기평가 원문(answersJson) 마스킹된 DTO 를 반환받는다.
     *  LEAD / 본인 / HR(권한자) 는 마스킹 없는 원문 노출.
     *
     *  권한 검증:
     *    - 본인(targetMemberId) : 항상 가능
     *    - calibration entry 보유자 : role 기반 마스킹
     *    - 그 외 : 403
     */
    @Transactional
    public EvaluationResponseResDto viewAsEvaluator(UUID responseId, UUID requesterId) {
        EvaluationResponse r = mustGet(responseId);
        if (r.getTargetMemberId().equals(requesterId)) {
            // 본인은 자기 응답 그대로 노출 (자기평가 원문 가시)
            return EvaluationResponseResDto.from(r);
        }
        if (r.getEvaluatorId() != null && r.getEvaluatorId().equals(requesterId)) {
            ensureLeadCalibrationExists(r);
            return EvaluationResponseResDto.from(r);
        }
        EvaluationCalibration entry = calibrationRepository
                .findByResponseIdAndEvaluatorId(responseId, requesterId)
                .orElse(null);
        if (entry == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "이 응답에 배정된 평가자가 아닙니다.");
        }
        if (entry.getRole() == CalibrationRole.LEAD) {
            return EvaluationResponseResDto.from(r);
        }
        // ASSISTANT : 자기평가 원문 마스킹
        return EvaluationResponseResDto.fromForAssistant(r);
    }

    /**
     * Lead 재할당 — HR 운영. EvaluationResponse.evaluatorId 변경 + LEAD calibration 의 evaluatorId 도 동시 변경.
     * 시즌 활성화 후 인사 이동 / 매니저 변경에 대응.
     */
    @Transactional
    public EvaluationResponseResDto reassignLead(UUID responseId, UUID newLeadId, UUID actorId, String reason) {
        EvaluationResponse r = mustGet(responseId);
        r.reassignLead(newLeadId, actorId, reason);

        EvaluationCalibration leadEntry = calibrationRepository
                .findByResponseIdAndRole(responseId, CalibrationRole.LEAD)
                .orElse(null);
        if (leadEntry != null) {
            // LEAD entry 의 evaluator_id 를 새 Lead 로 갱신 — unique(response_id, evaluator_id) 충돌 시
            // 기존 동일 ID ASSISTANT 가 있을 수 있어 사전 정리 후 재배치 필요
            calibrationRepository.findByResponseIdAndEvaluatorId(responseId, newLeadId)
                    .ifPresent(existing -> {
                        if (existing.getRole() != CalibrationRole.LEAD) {
                            calibrationRepository.delete(existing);
                            calibrationRepository.flush();
                        }
                    });
            calibrationRepository.delete(leadEntry);
            calibrationRepository.flush();
            calibrationRepository.save(EvaluationCalibration.builder()
                    .responseId(responseId)
                    .evaluatorId(newLeadId)
                    .role(CalibrationRole.LEAD)
                    .build());
        } else {
            // LEAD entry 가 없는 비정상 케이스 — 신규 생성
            calibrationRepository.save(EvaluationCalibration.builder()
                    .responseId(responseId)
                    .evaluatorId(newLeadId)
                    .role(CalibrationRole.LEAD)
                    .build());
        }

        return EvaluationResponseResDto.from(r);
    }

    /**
     * 시즌 중 퇴사 처리 — HR 운영. SKIPPED_LEAVER 전이.
     * 결과 공개 검증/면담 자동 생성에서 자연스럽게 제외됨.
     */
    @Transactional
    public EvaluationResponseResDto skipLeaver(UUID responseId, String reason) {
        EvaluationResponse r = mustGet(responseId);
        r.skipForLeaver();
        if (reason != null && !reason.isBlank()) {
            calibrationRepository.findByResponseIdAndRole(responseId, CalibrationRole.LEAD).ifPresent(lead -> {
                String prefixed = "[skip-leaver reason] " + reason
                        + (lead.getComment() != null ? "\n--\n" + lead.getComment() : "");
                lead.saveFinal(lead.getFinalGradeJson(), prefixed);
            });
        }
        return EvaluationResponseResDto.from(r);
    }

    /**
     * 공개 후 정정 — HR 운영. CONFIRMED → CALIBRATION_OPEN.
     * 기존 EvaluationResponse.unconfirm() 도메인 메서드 호출.
     * Lead 가 finalGradeJson 수정 후 재confirm 하면 aggregate 자동 갱신.
     */
    @Transactional
    public EvaluationResponseResDto reopenForCorrection(UUID responseId, String reason) {
        EvaluationResponse r = mustGet(responseId);
        r.unconfirm();
        // 사유 기록 — calibration 의 LEAD comment 에 prefix 형태로 보존
        if (reason != null && !reason.isBlank()) {
            calibrationRepository.findByResponseIdAndRole(responseId, CalibrationRole.LEAD).ifPresent(lead -> {
                String prefixed = "[reopen 사유] " + reason
                        + (lead.getComment() != null ? "\n--\n" + lead.getComment() : "");
                lead.saveFinal(lead.getFinalGradeJson(), prefixed);
            });
        }
        return EvaluationResponseResDto.from(r);
    }

    /**
     * 이의제기 — 본인 요청.
     */
    @Transactional
    public EvaluationResponseResDto requestObjection(UUID responseId, UUID memberId, String message) {
        EvaluationResponse r = mustGet(responseId);
        if (!r.getTargetMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 응답에 대해서만 이의제기 가능합니다.");
        }
        // 결과 공개 후에만 가능
        if (r.getGroup() == null || r.getGroup().getSeason() == null
                || r.getGroup().getSeason().getResultsPublishedAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "결과 공개 후에만 이의제기 가능합니다.");
        }
        r.requestObjection(message);
        return EvaluationResponseResDto.from(r);
    }

    /**
     * 이의제기 검토 시작 — HR.
     */
    @Transactional
    public EvaluationResponseResDto reviewObjection(UUID responseId) {
        EvaluationResponse r = mustGet(responseId);
        r.reviewObjection();
        return EvaluationResponseResDto.from(r);
    }

    /**
     * 이의제기 종결 — HR. resolution 메시지 기록.
     */
    @Transactional
    public EvaluationResponseResDto resolveObjection(UUID responseId, UUID resolverId, String resolution) {
        EvaluationResponse r = mustGet(responseId);
        r.resolveObjection(resolverId, resolution);
        return EvaluationResponseResDto.from(r);
    }
}
