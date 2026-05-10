package com._team._team.evaluation.service;

import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.EvaluationSeason;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.domain.enums.SeasonStatus;
import com._team._team.evaluation.domain.enums.SeasonType;
import com._team._team.evaluation.dto.reqdto.SeasonCreateReqDto;
import com._team._team.evaluation.dto.resdto.SeasonOperationalAlertsResDto;
import com._team._team.evaluation.dto.reqdto.SeasonUpdateReqDto;
import com._team._team.evaluation.dto.resdto.PublishBlockersResDto;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.repository.EvaluationGroupRepository;
import com._team._team.evaluation.repository.EvaluationSeasonRepository;
import com._team._team.evaluation.util.SeasonTargetCycleRules;
import com._team._team.goal.domain.enums.KpiCycle;
import com._team._team.goal.feignclients.MemberServiceClient;
import com._team._team.goal.feignclients.dto.MemberOrgContextDto;
import com._team._team.meeting.service.MeetingRecordService;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EvaluationSeasonService {

    private final EvaluationSeasonRepository seasonRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationGroupRepository groupRepository;
    private final SeasonActivationService activationService;
    private final MeetingRecordService meetingRecordService;
    private final MemberServiceClient memberServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    public EvaluationSeasonService(EvaluationSeasonRepository seasonRepository,
                                   EvaluationResponseRepository responseRepository,
                                   EvaluationGroupRepository groupRepository,
                                   SeasonActivationService activationService,
                                   MeetingRecordService meetingRecordService,
                                   MemberServiceClient memberServiceClient,
                                   ApplicationEventPublisher eventPublisher) {
        this.seasonRepository = seasonRepository;
        this.responseRepository = responseRepository;
        this.groupRepository = groupRepository;
        this.activationService = activationService;
        this.meetingRecordService = meetingRecordService;
        this.memberServiceClient = memberServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EvaluationSeason create(UUID companyId, SeasonCreateReqDto dto) {
        validateDates(dto.getStartDate(), dto.getEndDate());
        KpiCycle targetCycle = resolveTargetCycle(dto.getType());
        SeasonTargetCycleRules.validate(targetCycle, dto.getTargetCycleStart());

        EvaluationSeason season = EvaluationSeason.builder()
                .companyId(companyId)
                .name(dto.getName())
                .type(dto.getType())
                .targetCycle(targetCycle)
                .targetCycleStart(dto.getTargetCycleStart())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(SeasonStatus.DRAFT)
                .resultPublishDate(dto.getResultPublishDate())
                .scheduleJson(dto.getScheduleJson())
                .build();
        return seasonRepository.save(season);
    }

    @Transactional
    public EvaluationSeason update(UUID seasonId, UUID companyId, SeasonUpdateReqDto dto) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        if (season.getStatus() != SeasonStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only draft seasons can be edited.");
        }

        LocalDate nextStart = dto.getStartDate() != null ? dto.getStartDate() : season.getStartDate();
        LocalDate nextEnd = dto.getEndDate() != null ? dto.getEndDate() : season.getEndDate();
        validateDates(nextStart, nextEnd);

        SeasonType nextType = dto.getType() != null ? dto.getType() : season.getType();
        KpiCycle nextTargetCycle = resolveTargetCycle(nextType);
        LocalDate nextTargetCycleStart = dto.getTargetCycleStart() != null
                ? dto.getTargetCycleStart()
                : season.getTargetCycleStart();
        if (nextTargetCycleStart == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "targetCycleStart is required.");
        }
        SeasonTargetCycleRules.validate(nextTargetCycle, nextTargetCycleStart);

        season.update(
                dto.getName() != null ? dto.getName() : season.getName(),
                nextType,
                nextTargetCycle,
                nextTargetCycleStart,
                nextStart,
                nextEnd,
                dto.getResultPublishDate() != null ? dto.getResultPublishDate() : season.getResultPublishDate(),
                dto.getScheduleJson() != null ? dto.getScheduleJson() : season.getScheduleJson()
        );
        return season;
    }

    @Transactional
    public void delete(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        if (season.getStatus() != SeasonStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only draft seasons can be deleted.");
        }
        if (!responseRepository.findByGroup_Season_SeasonId(seasonId).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Evaluation responses already exist for this season.");
        }
        groupRepository.deleteAll(groupRepository.findBySeason_SeasonId(seasonId));
        seasonRepository.delete(season);
    }

    @Transactional
    public void openSelfEval(UUID seasonId, UUID companyId, List<SeasonActivationService.TargetSpec> targetSpecs) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        activationService.activate(season, targetSpecs);
    }

    @Transactional
    public void advanceToManagerEval(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        season.openManagerEval();
    }

    @Transactional
    public void advanceToGradeConfirm(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        season.openGradeConfirm();
    }

    @Transactional
    public void publishResults(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        if (season.isResultsPublished()) {
            return;
        }

        List<EvaluationResponse> responses = responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .toList();

        long pendingCount = responses.stream()
                .filter(r -> r.getStage() != EvaluationStage.CONFIRMED)
                .filter(r -> r.getStage() != EvaluationStage.SKIPPED_LEAVER)
                .count();
        if (pendingCount > 0) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot publish while non-finalized responses remain: " + pendingCount
            );
        }

        season.publishResults();
        meetingRecordService.createFeedbackMeetingsForSeason(seasonId, companyId);
    }

    @Transactional
    public void openInterview(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        season.openInterview();
    }

    @Transactional
    public void close(UUID seasonId, UUID companyId) {
        EvaluationSeason season = mustGet(seasonId, companyId);
        season.close();
    }

    @Transactional(readOnly = true)
    public EvaluationSeason get(UUID seasonId, UUID companyId) {
        return mustGet(seasonId, companyId);
    }

    @Transactional(readOnly = true)
    public List<EvaluationSeason> list(UUID companyId) {
        return seasonRepository.findByCompanyIdOrderByStartDateDesc(companyId);
    }

    @Transactional(readOnly = true)
    public PublishBlockersResDto getPublishBlockers(UUID seasonId, UUID companyId) {
        mustGet(seasonId, companyId);

        List<EvaluationResponse> responses = responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .toList();

        return buildPublishBlockers(seasonId, responses);
    }

    @Transactional(readOnly = true)
    public PublishBlockersResDto getPublishBlockers(
            UUID seasonId,
            UUID companyId,
            EvaluationAccessScopeService.AccessScope scope
    ) {
        mustGet(seasonId, companyId);

        List<EvaluationResponse> responses = responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .filter(r -> canSeeInOperation(r, scope))
                .toList();

        return buildPublishBlockers(seasonId, responses);
    }

    private PublishBlockersResDto buildPublishBlockers(UUID seasonId, List<EvaluationResponse> responses) {
        Map<String, Integer> byStage = new LinkedHashMap<>();
        for (EvaluationStage stage : EvaluationStage.values()) {
            byStage.put(stage.name(), 0);
        }
        for (EvaluationResponse response : responses) {
            byStage.merge(response.getStage().name(), 1, Integer::sum);
        }

        List<PublishBlockersResDto.BlockerEntry> blockers = responses.stream()
                .filter(r -> r.getStage() != EvaluationStage.CONFIRMED)
                .filter(r -> r.getStage() != EvaluationStage.SKIPPED_LEAVER)
                .map(r -> PublishBlockersResDto.BlockerEntry.builder()
                        .responseId(r.getResponseId())
                        .targetMemberId(r.getTargetMemberId())
                        .evaluatorId(r.getEvaluatorId())
                        .stage(r.getStage())
                        .build())
                .toList();

        return PublishBlockersResDto.builder()
                .seasonId(seasonId)
                .totalResponses(responses.size())
                .byStage(byStage)
                .blockers(blockers)
                .publishable(blockers.isEmpty())
                .build();
    }

    @Transactional(readOnly = true)
    public SeasonOperationalAlertsResDto getOperationalAlerts(UUID seasonId, UUID companyId) {
        mustGet(seasonId, companyId);

        List<EvaluationResponse> responses = responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .toList();

        List<SeasonOperationalAlertsResDto.AlertItem> alerts = responses.stream()
                .flatMap(response -> buildOperationalAlerts(response).stream())
                .toList();

        return buildOperationalAlertsResponse(seasonId, alerts);
    }

    @Transactional(readOnly = true)
    public SeasonOperationalAlertsResDto getOperationalAlerts(
            UUID seasonId,
            UUID companyId,
            EvaluationAccessScopeService.AccessScope scope
    ) {
        mustGet(seasonId, companyId);

        List<EvaluationResponse> responses = responseRepository.findByGroup_Season_SeasonId(seasonId).stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .filter(r -> canSeeInOperation(r, scope))
                .toList();

        List<SeasonOperationalAlertsResDto.AlertItem> alerts = responses.stream()
                .flatMap(response -> buildOperationalAlerts(response).stream())
                .toList();

        return buildOperationalAlertsResponse(seasonId, alerts);
    }

    private SeasonOperationalAlertsResDto buildOperationalAlertsResponse(
            UUID seasonId,
            List<SeasonOperationalAlertsResDto.AlertItem> alerts
    ) {
        return SeasonOperationalAlertsResDto.builder()
                .seasonId(seasonId)
                .totalAlerts(alerts.size())
                .alerts(alerts)
                .build();
    }

    private boolean canSeeInOperation(EvaluationResponse response, EvaluationAccessScopeService.AccessScope scope) {
        if (scope == null || scope.companyWide()) {
            return true;
        }
        return scope.canSeeTarget(response.getTargetMemberId())
                || scope.isRequester(response.getEvaluatorId());
    }

    private List<SeasonOperationalAlertsResDto.AlertItem> buildOperationalAlerts(EvaluationResponse response) {
        if (response.getStage() == EvaluationStage.SKIPPED_LEAVER) {
            return List.of();
        }

        MemberOrgContextDto targetContext = memberServiceClient.getOrgContext(response.getTargetMemberId());
        MemberOrgContextDto evaluatorContext = memberServiceClient.getOrgContext(response.getEvaluatorId());

        List<SeasonOperationalAlertsResDto.AlertItem> alerts = new ArrayList<>();

        String targetStatus = targetContext != null && targetContext.getMemberStatus() != null
                ? targetContext.getMemberStatus()
                : "UNKNOWN";
        String evaluatorStatus = evaluatorContext != null && evaluatorContext.getMemberStatus() != null
                ? evaluatorContext.getMemberStatus()
                : "UNKNOWN";

        if ("DORMANT".equalsIgnoreCase(targetStatus)) {
            alerts.add(SeasonOperationalAlertsResDto.AlertItem.builder()
                    .alertType("TARGET_DORMANT")
                    .severity("HIGH")
                    .responseId(response.getResponseId())
                    .targetMemberId(response.getTargetMemberId())
                    .evaluatorId(response.getEvaluatorId())
                    .targetMemberStatus(targetStatus)
                    .evaluatorStatus(evaluatorStatus)
                    .message("평가 대상자가 현재 DORMANT 상태입니다. 이번 시즌 평가 제외 여부를 확인하세요.")
                    .recommendedAction("SKIP_LEAVER")
                    .build());
        } else if ("LEAVE".equalsIgnoreCase(targetStatus)) {
            alerts.add(SeasonOperationalAlertsResDto.AlertItem.builder()
                    .alertType("TARGET_LEAVE")
                    .severity("MEDIUM")
                    .responseId(response.getResponseId())
                    .targetMemberId(response.getTargetMemberId())
                    .evaluatorId(response.getEvaluatorId())
                    .targetMemberStatus(targetStatus)
                    .evaluatorStatus(evaluatorStatus)
                    .message("평가 대상자가 현재 LEAVE 상태입니다. 평가 유지 여부를 검토하세요.")
                    .recommendedAction("REVIEW_OR_SKIP")
                    .build());
        }

        if (response.getStage() != EvaluationStage.CONFIRMED
                && !"ACTIVE".equalsIgnoreCase(evaluatorStatus)) {
            alerts.add(SeasonOperationalAlertsResDto.AlertItem.builder()
                    .alertType("LEAD_INACTIVE")
                    .severity("HIGH")
                    .responseId(response.getResponseId())
                    .targetMemberId(response.getTargetMemberId())
                    .evaluatorId(response.getEvaluatorId())
                    .targetMemberStatus(targetStatus)
                    .evaluatorStatus(evaluatorStatus)
                    .message("현재 Lead evaluator가 ACTIVE 상태가 아닙니다. 새 Lead 지정이 필요할 수 있습니다.")
                    .recommendedAction("REASSIGN_LEAD")
                    .build());
        }

        return alerts;
    }

    private EvaluationSeason mustGet(UUID seasonId, UUID companyId) {
        EvaluationSeason season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Season not found."));
        if (!season.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Season belongs to another company.");
        }
        return season;
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Season dates are invalid.");
        }
    }

    private static KpiCycle resolveTargetCycle(SeasonType type) {
        return switch (type) {
            case QUARTER -> KpiCycle.QUARTERLY;
            case HALF_YEAR -> KpiCycle.HALF_YEARLY;
            case ANNUAL -> KpiCycle.YEARLY;
        };
    }
}
