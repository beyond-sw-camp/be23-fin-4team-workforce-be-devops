package com._team._team.meeting.service;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.domain.EvaluationCalibration;
import com._team._team.evaluation.domain.EvaluationResponse;
import com._team._team.evaluation.domain.enums.CalibrationRole;
import com._team._team.evaluation.domain.enums.EvaluationStage;
import com._team._team.evaluation.repository.EvaluationCalibrationRepository;
import com._team._team.evaluation.repository.EvaluationResponseRepository;
import com._team._team.evaluation.service.EvaluationAccessScopeService;
import com._team._team.meeting.domain.MeetingRecord;
import com._team._team.meeting.domain.enums.RepeatCycle;
import com._team._team.meeting.dto.reqdto.MeetingRecordCompleteReqDto;
import com._team._team.meeting.dto.reqdto.MeetingRecordCreateReqDto;
import com._team._team.meeting.dto.reqdto.MeetingRecordUpdateReqDto;
import com._team._team.meeting.dto.reqdto.MemberReactionReqDto;
import com._team._team.meeting.dto.resdto.MeetingRecordResDto;
import com._team._team.meeting.repository.MeetingRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MeetingRecordService {

    private static final Logger log = LoggerFactory.getLogger(MeetingRecordService.class);

    final MeetingRecordRepository meetingRecordRepository;
    final EvaluationResponseRepository evaluationResponseRepository;
    final EvaluationCalibrationRepository evaluationCalibrationRepository;

    @Autowired
    public MeetingRecordService(MeetingRecordRepository meetingRecordRepository,
                                EvaluationResponseRepository evaluationResponseRepository,
                                EvaluationCalibrationRepository evaluationCalibrationRepository) {
        this.meetingRecordRepository = meetingRecordRepository;
        this.evaluationResponseRepository = evaluationResponseRepository;
        this.evaluationCalibrationRepository = evaluationCalibrationRepository;
    }

    /**
     * 평가 결과 공개 직후, 해당 시즌의 CONFIRMED 응답마다 "평가 결과 피드백 면담" 을 자동 생성.
     *
     * <p>설계 (단일 응답 모델, OKR-style v3):</p>
     * <ul>
     *     <li>응답은 멤버당 1건(SELF). 다면 perspectives 는 EvaluationCalibration 의 LEAD/ASSISTANT role 로 보관.</li>
     *     <li>매니저(LEAD)는 EvaluationCalibration where role=LEAD 의 evaluatorId 로 결정.
     *         LEAD calibration 이 없으면 fallback 으로 response.evaluatorId(=시즌 활성화 시점의 Lead) 사용.</li>
     *     <li>대상 직원 1명당 한 시즌에 피드백 면담 1건만 생성 (멱등성).</li>
     *     <li>예약 시점은 호출 시점 + 3일. 관리자가 이후 수정 가능.</li>
     *     <li>CONFIRMED 가 아닌 응답(SELF_PENDING / CALIBRATION_OPEN / SKIPPED_LEAVER 등)은 면담 생성에서 제외.</li>
     * </ul>
     */
    @Transactional
    public void createFeedbackMeetingsForSeason(UUID seasonId, UUID companyId) {
        // v4: 멱등성을 "시즌 내 멤버 단위" 로 강화 — 이미 어느 멤버에 면담이 생성됐어도
        //     다른 멤버 응답이 추가 CONFIRMED 되면 추가 생성. 같은 멤버에 중복 생성 방지.
        Set<UUID> alreadyScheduled = new HashSet<>();
        meetingRecordRepository.findByRelatedSeasonIdAndCompanyId(seasonId, companyId)
                .forEach(m -> alreadyScheduled.add(m.getMemberId()));
        int created = 0;
        int skipped = 0;

        List<EvaluationResponse> responses = evaluationResponseRepository.findByGroup_Season_SeasonId(seasonId);
        for (EvaluationResponse response : responses) {
            if (!response.getCompanyId().equals(companyId)) continue;
            // CONFIRMED 가 아닌 응답은 피드백 면담 대상 아님 (계측만)
            if (response.getStage() != EvaluationStage.CONFIRMED) {
                skipped++;
                continue;
            }

            UUID memberId = response.getTargetMemberId();
            if (memberId == null) continue;
            if (!alreadyScheduled.add(memberId)) continue;

            UUID managerId = resolveLeadEvaluatorId(response);
            if (managerId == null) {
                log.warn("[feedback-meeting] LEAD 평가자 미정 — 면담 스킵 responseId={}", response.getResponseId());
                continue;
            }
            if (managerId.equals(memberId)) {
                // 본인이 LEAD 인 비정상 케이스 방어
                log.warn("[feedback-meeting] member==manager, 면담 스킵 memberId={}", memberId);
                continue;
            }

            MeetingRecord feedback = MeetingRecord.builder()
                    .memberId(memberId)
                    .managerId(managerId)
                    .repeatCycle(RepeatCycle.ONE_TIME)
                    .scheduledAt(defaultFeedbackMeetingTime())
                    .agenda("평가 결과 피드백 면담 — 시즌: " + seasonId)
                    .relatedEvaluationResponseId(response.getResponseId())
                    .relatedSeasonId(seasonId)
                    .companyId(companyId)
                    .build();
            // goalSnapshotJson 에서 goalId 목록 추출 → 면담에 연결
            String snap = response.getGoalSnapshotJson();
            if (snap != null && !snap.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode root =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(snap);
                    com.fasterxml.jackson.databind.JsonNode goalsNode = root.isArray() ? root : root.get("goals");
                    if (goalsNode != null && goalsNode.isArray() && goalsNode.size() > 0) {
                        List<String> ids = new java.util.ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode n : goalsNode) {
                            com.fasterxml.jackson.databind.JsonNode idNode = n.get("goalId");
                            if (idNode != null && !idNode.isNull()) ids.add(idNode.asText());
                        }
                        if (!ids.isEmpty()) {
                            feedback.linkGoals(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ids));
                        }
                    }
                } catch (Exception ignore) {
                    // 스냅샷 파싱 실패해도 미팅 생성 자체는 진행
                }
            }
            meetingRecordRepository.save(feedback);
            created++;
        }
        log.info("[feedback-meeting] season={} created={} skipped(non-confirmed)={}",
                seasonId, created, skipped);
    }

    /**
     * v4 — 운영자 수동 재생성 (멱등). 누락된 멤버에 대해서만 추가 생성.
     *  publishResults 트랜잭션에서 면담 생성이 실패했거나, 부분 누락 발생 시 복구용.
     */
    @Transactional
    public void regenerateFeedbackMeetingsForSeason(UUID seasonId, UUID companyId) {
        createFeedbackMeetingsForSeason(seasonId, companyId);
    }

    /**
     * v4 — 시즌별 면담 진행 현황 (운영자 대시보드).
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Integer> getSeasonStatus(UUID seasonId, UUID companyId) {
        List<MeetingRecord> meetings = meetingRecordRepository
                .findByRelatedSeasonIdAndCompanyId(seasonId, companyId);
        return buildStatus(meetings);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Integer> getSeasonStatus(
            UUID seasonId,
            UUID companyId,
            EvaluationAccessScopeService.AccessScope scope
    ) {
        List<MeetingRecord> meetings = meetingRecordRepository
                .findByRelatedSeasonIdAndCompanyId(seasonId, companyId)
                .stream()
                .filter(meeting -> canSeeInOperation(meeting, scope))
                .toList();
        return buildStatus(meetings);
    }

    private java.util.Map<String, Integer> buildStatus(List<MeetingRecord> meetings) {
        int created = meetings.size();
        int completed = (int) meetings.stream().filter(m -> m.getCompletedAt() != null).count();
        int unscheduled = (int) meetings.stream().filter(m -> m.getScheduledAt() == null).count();
        java.util.Map<String, Integer> status = new java.util.LinkedHashMap<>();
        status.put("createdCount", created);
        status.put("completedCount", completed);
        status.put("uncompletedCount", created - completed);
        status.put("unscheduledCount", unscheduled);
        return status;
    }

    @Transactional(readOnly = true)
    public List<MeetingRecordResDto> listBySeason(UUID seasonId, UUID companyId) {
        return meetingRecordRepository.findByRelatedSeasonIdAndCompanyId(seasonId, companyId)
                .stream()
                .map(MeetingRecordResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingRecordResDto> listBySeason(
            UUID seasonId,
            UUID companyId,
            EvaluationAccessScopeService.AccessScope scope
    ) {
        return meetingRecordRepository.findByRelatedSeasonIdAndCompanyId(seasonId, companyId)
                .stream()
                .filter(meeting -> canSeeInOperation(meeting, scope))
                .map(MeetingRecordResDto::from)
                .collect(Collectors.toList());
    }

    private boolean canSeeInOperation(MeetingRecord meeting, EvaluationAccessScopeService.AccessScope scope) {
        if (scope == null || scope.companyWide()) {
            return true;
        }
        return scope.canSeeTarget(meeting.getMemberId())
                || scope.isRequester(meeting.getManagerId());
    }

    /**
     * 응답의 LEAD 평가자 결정.
     *  1) EvaluationCalibration where role=LEAD → evaluatorId
     *  2) fallback : response.evaluatorId (시즌 활성화 시점에 Lead 로 매핑된 값)
     */
    private UUID resolveLeadEvaluatorId(EvaluationResponse response) {
        EvaluationCalibration lead = evaluationCalibrationRepository
                .findByResponseIdAndRole(response.getResponseId(), CalibrationRole.LEAD)
                .orElse(null);
        if (lead != null && lead.getEvaluatorId() != null) {
            return lead.getEvaluatorId();
        }
        return response.getEvaluatorId();
    }

    @Transactional
    public MeetingRecordResDto createMeeting(MeetingRecordCreateReqDto dto, UUID companyId) {
        // [M3] memberId == managerId 인 셀프 미팅은 비정상 케이스로 차단
        if (dto.getMemberId() != null && dto.getMemberId().equals(dto.getManagerId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "본인 자신을 매니저로 둔 1:1 미팅은 생성할 수 없습니다.");
        }
        if (dto.getScheduledAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "면담 예정 일시는 필수입니다.");
        }
        MeetingRecord parentRecord = null;
        if (dto.getParentRecordId() != null) {
            parentRecord = meetingRecordRepository.findById(dto.getParentRecordId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "상위 면담 기록을 찾을 수 없습니다. id=" + dto.getParentRecordId()));
            // 부모 미팅과 memberId/managerId 조합이 달라지면 의미상 혼동되므로 차단
            if (!parentRecord.getMemberId().equals(dto.getMemberId())
                    || !parentRecord.getManagerId().equals(dto.getManagerId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "상위 면담과 동일한 멤버/매니저 조합이어야 합니다.");
            }
        }

        MeetingRecord meetingRecord = MeetingRecord.builder()
                .parentRecord(parentRecord)
                .memberId(dto.getMemberId())
                .managerId(dto.getManagerId())
                .repeatCycle(dto.getRepeatCycle())
                .scheduledAt(dto.getScheduledAt())
                .agenda(dto.getAgenda())
                .companyId(companyId)
                .build();

        MeetingRecord saved = meetingRecordRepository.save(meetingRecord);
        return MeetingRecordResDto.from(saved);
    }

    private LocalDateTime defaultFeedbackMeetingTime() {
        LocalDateTime base = LocalDateTime.now().plusDays(3);
        return base.withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * [M9] 미팅에서 논의될 연관 목표 ID 목록을 등록/수정합니다.
     */
    @Transactional
    public MeetingRecordResDto linkGoals(UUID meetingRecordId, UUID requesterId, String relatedGoalIdsJson) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));
        if (!meetingRecord.getManagerId().equals(requesterId)
                && !meetingRecord.getMemberId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 면담 기록의 당사자만 목표를 연결할 수 있습니다.");
        }
        meetingRecord.linkGoals(relatedGoalIdsJson);
        return MeetingRecordResDto.from(meetingRecord);
    }

    @Transactional(readOnly = true)
    public MeetingRecordResDto getMeeting(UUID meetingRecordId, UUID requesterId) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 당사자 확인
        if (!meetingRecord.getMemberId().equals(requesterId) && !meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 면담 기록을 조회할 권한이 없습니다.");
        }

        MeetingRecordResDto dto = MeetingRecordResDto.from(meetingRecord);
        // privateMemo 는 매니저에게만 노출 — 멤버가 조회하면 null 처리
        if (!meetingRecord.getManagerId().equals(requesterId)) {
            dto.setPrivateMemo(null);
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<MeetingRecordResDto> listByMember(UUID memberId) {
        return meetingRecordRepository.findByMemberIdOrderByScheduledAtDesc(memberId)
                .stream()
                .map(MeetingRecordResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingRecordResDto> listByManager(UUID managerId) {
        return meetingRecordRepository.findByManagerIdOrderByScheduledAtDesc(managerId)
                .stream()
                .map(MeetingRecordResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingRecordResDto> listByMemberAndManager(UUID memberId, UUID managerId) {
        return meetingRecordRepository.findByMemberIdAndManagerIdOrderByScheduledAtDesc(memberId, managerId)
                .stream()
                .map(MeetingRecordResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public MeetingRecordResDto completeMeeting(UUID meetingRecordId, UUID requesterId, MeetingRecordCompleteReqDto dto) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 매니저 확인
        if (!meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 면담을 완료 처리할 수 있습니다.");
        }

        if (meetingRecord.getCompletedAt() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 완료된 면담 기록입니다.");
        }
        // [M1] 완료 시 memo 는 필수 — 회의록 없는 "완료" 는 회고/후속조치 추적을 막는다.
        if (dto.getMemo() == null || dto.getMemo().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "면담 완료 시 메모(memo)는 필수입니다.");
        }
        try {
            meetingRecord.complete(dto.getMemo(), dto.getManagerReaction());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (dto.getPrivateMemo() != null) {
            meetingRecord.updatePrivateMemo(dto.getPrivateMemo());
        }
        return MeetingRecordResDto.from(meetingRecord);
    }

    @Transactional
    public MeetingRecordResDto updateMeeting(UUID meetingRecordId, UUID requesterId, MeetingRecordUpdateReqDto dto) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 매니저 확인
        if (!meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 면담 일정을 수정할 수 있습니다.");
        }

        if (dto.getScheduledAt() != null) {
            meetingRecord.updateScheduledAt(dto.getScheduledAt());
        }
        if (dto.getAgenda() != null) {
            meetingRecord.updateAgenda(dto.getAgenda());
        }
        if (dto.getRepeatCycle() != null) {
            meetingRecord.updateRepeatCycle(dto.getRepeatCycle());
        }
        return MeetingRecordResDto.from(meetingRecord);
    }

    @Transactional
    public MeetingRecordResDto recordMemberReaction(UUID meetingRecordId, UUID requesterId, MemberReactionReqDto dto) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 멤버 확인
        if (!meetingRecord.getMemberId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인의 면담에 대해서만 반응을 기록할 수 있습니다.");
        }

        meetingRecord.recordMemberReaction(dto.getMemberReaction());
        return MeetingRecordResDto.from(meetingRecord);
    }

    @Transactional
    public MeetingRecordResDto updatePrivateMemo(UUID meetingRecordId, UUID requesterId, String privateMemo) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 매니저 확인
        if (!meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 비공개 메모를 작성할 수 있습니다.");
        }

        meetingRecord.updatePrivateMemo(privateMemo);
        return MeetingRecordResDto.from(meetingRecord);
    }
}
