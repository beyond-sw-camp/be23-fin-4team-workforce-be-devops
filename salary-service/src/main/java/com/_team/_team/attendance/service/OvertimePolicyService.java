package com._team._team.attendance.service;

import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.dto.reqDto.OvertimePolicyCreateReqDto;
import com._team._team.attendance.dto.reqDto.OvertimePolicyUpdateReqDto;
import com._team._team.attendance.dto.resDto.OvertimePolicyResDto;
import com._team._team.attendance.publisher.RagSyncAttendanceEventPublisher;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncAttendanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 연장근로 정책 관리 서비스
 */
@Service
@Transactional
public class OvertimePolicyService {
    private final OvertimePolicyRepository overtimePolicyRepository;
    private final RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher;

    @Autowired
    public OvertimePolicyService(OvertimePolicyRepository overtimePolicyRepository,
                                 RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher) {
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.ragSyncAttendanceEventPublisher = ragSyncAttendanceEventPublisher;
    }

    /**
     * 정책 신규 생성
     */
    public OvertimePolicyResDto create(UUID companyId, OvertimePolicyCreateReqDto reqDto) {

        // 법정 한도 검증
        validateLegalLimits(
                reqDto.getWeeklyOvertimeLimitMinutes(),
                reqDto.getWeeklyTotalLimitMinutes(),
                reqDto.getDailyOvertimeLimitMinutes(),
                reqDto.getMonthlyOvertimeLimitMinutes());

        // 기존 활성 정책이 있으면 종료일 지정 (신규 시작일 하루 전)
        overtimePolicyRepository.findByCompanyIdAndEffectiveToIsNull(companyId)
                .ifPresent(existing -> existing.close(reqDto.getEffectiveFrom().minusDays(1)));

        OvertimePolicy saved = overtimePolicyRepository.save(reqDto.toEntity(companyId));

        // RAG 동기화 이벤트 발행
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("OVERTIME_POLICY")
                        .resourceId(saved.getOvertimePolicyId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return OvertimePolicyResDto.fromEntity(saved);
    }

    /**
     * 정책 수정
     * 같은 정책 버전의 내용만 수정, 새 버전을 원하면 create 호출
     */
    public OvertimePolicyResDto update(UUID policyId,
                                       UUID companyId,
                                       OvertimePolicyUpdateReqDto reqDto) {

        // 법정 한도 검증
        validateLegalLimits(
                reqDto.getWeeklyOvertimeLimitMinutes(),
                reqDto.getWeeklyTotalLimitMinutes(),
                reqDto.getDailyOvertimeLimitMinutes(),
                reqDto.getMonthlyOvertimeLimitMinutes());

        OvertimePolicy policy = findPolicy(policyId, companyId);
        policy.update(
                reqDto.getOvertimeFloorMinutes(),
                reqDto.getApprovalMode(),
                reqDto.getPostApprovalDeadlineHours(),
                reqDto.getWeeklyOvertimeLimitMinutes(),
                reqDto.getWeeklyTotalLimitMinutes(),
                reqDto.getDailyOvertimeLimitMinutes(),
                reqDto.getMonthlyOvertimeLimitMinutes(),
                reqDto.getHolidayWorkRequiresApproval()
        );

        // RAG 동기화 이벤트 발행
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("OVERTIME_POLICY")
                        .resourceId(policyId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return OvertimePolicyResDto.fromEntity(policy);
    }

    /**
     * 현재 적용 중인 정책
     */
    @Transactional(readOnly = true)
    public OvertimePolicyResDto findCurrent(UUID companyId) {
        return overtimePolicyRepository.findByCompanyIdAndEffectiveToIsNull(companyId)
                .map(OvertimePolicyResDto::fromEntity)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "활성 연장근로 정책이 없습니다."));
    }

    /**
     * 단건 조회
     */
    @Transactional(readOnly = true)
    public OvertimePolicyResDto findById(UUID policyId, UUID companyId) {
        return OvertimePolicyResDto.fromEntity(findPolicy(policyId, companyId));
    }

    /**
     * 회사 정책 이력 전체
     */
    @Transactional(readOnly = true)
    public List<OvertimePolicyResDto> findHistory(UUID companyId) {
        return overtimePolicyRepository
                .findAllByCompanyIdOrderByEffectiveFromDesc(companyId)
                .stream()
                .map(OvertimePolicyResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 정책 찾기
    private OvertimePolicy findPolicy(UUID policyId, UUID companyId) {
        OvertimePolicy policy = overtimePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "연장근로 정책을 찾을 수 없습니다."));

        if (!policy.getCompanyId().equals(companyId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 정책은 접근할 수 없습니다.");
        }
        return policy;
    }

    /**
     * 연장근로 정책 법정 한도 검증
     * 근로기준법 50조의2 (주 52시간), 53조 (주 12시간 연장) 기반
     */
    private void validateLegalLimits(Integer weeklyOvertimeLimit,
                                     Integer weeklyTotalLimit,
                                     Integer dailyOvertimeLimit,
                                     Integer monthlyOvertimeLimit) {

        // 1. 주 연장근로 법정 최대 12시간 (근기법 53조 1항)
        if (weeklyOvertimeLimit != null && weeklyOvertimeLimit > 720) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "주 연장근로 한도는 법정 12시간(720분)을 초과할 수 없습니다. (근로기준법 53조)");
        }

        // 2. 주 총 근로시간 법정 최대 52시간 (근기법 50조의2)
        if (weeklyTotalLimit != null && weeklyTotalLimit > 3120) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "주 총 근로시간은 법정 52시간(3120분)을 초과할 수 없습니다. (근로기준법 50조)");
        }

        // 3. 일 연장근로가 주 연장근로 한도보다 클 수 없음 (논리 일관성)
        if (dailyOvertimeLimit != null && weeklyOvertimeLimit != null
                && dailyOvertimeLimit > weeklyOvertimeLimit) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    String.format("일 연장근로 한도(%d분)가 주 한도(%d분)보다 클 수 없습니다.",
                            dailyOvertimeLimit, weeklyOvertimeLimit));
        }

        // 4. 월 연장근로가 주 연장근로 × 5주 초과 불가 (한 달 최대 5주 가정)
        if (monthlyOvertimeLimit != null && weeklyOvertimeLimit != null
                && monthlyOvertimeLimit > weeklyOvertimeLimit * 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    String.format("월 연장근로 한도(%d분)가 주 한도 × 5주(%d분)를 초과할 수 없습니다.",
                            monthlyOvertimeLimit, weeklyOvertimeLimit * 5));
        }
    }
}