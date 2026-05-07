package com._team._team.attendance.service;

import com._team._team.attendance.domain.CompanyLeaveType;
import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import com._team._team.attendance.dto.reqDto.LeavePromotionDesignateReqDto;
import com._team._team.attendance.dto.reqDto.LeavePromotionRespondReqDto;
import com._team._team.attendance.dto.reqDto.MemberBalanceUseReqDto;
import com._team._team.attendance.dto.resDto.LeavePromotionHistoryResDto;
import com._team._team.attendance.dto.resDto.LeavePromotionMyResDto;
import com._team._team.attendance.dto.resDto.LeavePromotionNoResponseResDto;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.CompanyLeaveTypeRepository;
import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.LeavePromotionLogRepository;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 연차 사용 촉진제 회신 처리 서비스, 직원 회신은 계획 기록만 잔여 차감 없음
@Slf4j
@Service
@Transactional
public class LeavePromotionResponseService {

    // 2차 통보 자동 강제 지정이 기본, 자동 실패한 예외 건은 즉시 수동 처리 가능하도록 0
    private static final int DESIGNATION_GRACE_DAYS = 0;

    private static final String DEFAULT_LEAVE_TYPE_CODE = "ANNUAL";

    private final LeavePromotionLogRepository promotionLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final MemberBalanceRepository memberBalanceRepository;
    private final CompanyLeaveTypeRepository companyLeaveTypeRepository;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final MemberBalanceService memberBalanceService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Autowired
    public LeavePromotionResponseService(LeavePromotionLogRepository promotionLogRepository,
                                         LeaveRequestRepository leaveRequestRepository,
                                         MemberBalanceRepository memberBalanceRepository,
                                         CompanyLeaveTypeRepository companyLeaveTypeRepository,
                                         CompanyHolidayRepository companyHolidayRepository,
                                         LeavePolicyRepository leavePolicyRepository,
                                         MemberBalanceService memberBalanceService,
                                         ApplicationEventPublisher eventPublisher) {
        this.promotionLogRepository = promotionLogRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.memberBalanceRepository = memberBalanceRepository;
        this.companyLeaveTypeRepository = companyLeaveTypeRepository;
        this.companyHolidayRepository = companyHolidayRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.memberBalanceService = memberBalanceService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // 직원 본인이 받은 촉진 통보 목록 응답 필요한 것 우선
    @Transactional(readOnly = true)
    public List<LeavePromotionMyResDto> findMyPromotions(UUID memberId) {
        List<LeavePromotionLog> logs = promotionLogRepository
                .findByMemberIdOrderBySentOnDesc(memberId);

        List<LeavePromotionMyResDto> result = new ArrayList<>();
        for (LeavePromotionLog log : logs) {
            MemberBalance bal = memberBalanceRepository
                    .findById(log.getMemberBalanceId()).orElse(null);
            LocalDate exp = bal != null ? bal.getExpirationDate() : null;
            Double remaining = bal != null ? bal.getRemaining() : null;
            result.add(LeavePromotionMyResDto.from(log, exp, remaining));
        }
        // SENT 우선 정렬 (응답 필요한 것 위로)
        result.sort(Comparator.comparing(d -> d.getStatus() != PromotionLogStatus.SENT));
        return result;
    }

    /**
     * 직원이 알림을 처음 열람했을 때 viewedAt 기록
     * 회신(acknowledge)보다 약한 단계 - 통보를 인지했다는 추가 증거로 활용
     */
    public void markViewed(UUID memberId, UUID promotionLogId) {
        LeavePromotionLog log = promotionLogRepository.findById(promotionLogId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "촉진 통보를 찾을 수 없습니다"));
        if (!log.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 통보만 열람 처리 가능합니다");
        }
        log.markViewed();
    }

    // 직원 회신 처리
    // 핵심 LeaveRequest 자동 생성 안 함 잔여 차감 없음 회사 면책 기록만
    public void respondAsMember(UUID memberId, UUID promotionLogId,
                                LeavePromotionRespondReqDto reqDto) {
        LeavePromotionLog log = promotionLogRepository.findById(promotionLogId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "촉진 통보를 찾을 수 없습니다"));

        if (!log.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 통보만 회신 가능합니다");
        }
        // SENT(미회신) 또는 ACKNOWLEDGED(재회신) 만 허용 - 강제 지정(DESIGNATED) 후엔 불가
        if (log.getStatus() != PromotionLogStatus.SENT
                && log.getStatus() != PromotionLogStatus.ACKNOWLEDGED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "회사가 강제 지정한 통보는 재회신할 수 없습니다");
        }

        String json = serializeDates(reqDto.getPlannedDates());
        // 재회신 시 휴가계획 날짜를 최신으로 덮어씀 (이력 별도 보관 X - 최신 회신만 유효)
        log.acknowledge(json);
    }

    // 회사 무응답자 리스트 (2차 통보 후 10일 경과만 노출)
    @Transactional(readOnly = true)
    public List<LeavePromotionNoResponseResDto> findNoResponse(UUID companyId,
                                                               LocalDate today) {
        List<LeavePromotionLog> logs = promotionLogRepository
                .findByCompanyIdAndStageAndStatus(
                        companyId, PromotionStage.SECOND, PromotionLogStatus.SENT);

        List<LeavePromotionNoResponseResDto> result = new ArrayList<>();
        for (LeavePromotionLog log : logs) {
            long daysSinceSent = ChronoUnit.DAYS.between(log.getSentOn(), today);
            if (daysSinceSent < DESIGNATION_GRACE_DAYS) continue;

            MemberBalance bal = memberBalanceRepository
                    .findById(log.getMemberBalanceId()).orElse(null);
            if (bal == null) continue;

            result.add(LeavePromotionNoResponseResDto.from(
                    log, bal.getExpirationDate(), bal.getRemaining(), daysSinceSent));
        }
        return result;
    }

    // 회사 강제 지정 노무수령 거부
    public void designateByAdmin(UUID adminId, UUID companyId, UUID promotionLogId,
                                 LeavePromotionDesignateReqDto reqDto, LocalDate today) {
        LeavePromotionLog log = promotionLogRepository.findById(promotionLogId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "촉진 통보를 찾을 수 없습니다"));

        if (!log.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "다른 회사의 통보는 처리할 수 없습니다");
        }
        if (log.getStage() != PromotionStage.SECOND) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "2차 통보 건만 강제 지정 가능합니다");
        }
        if (log.getStatus() != PromotionLogStatus.SENT) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이미 회신되었거나 강제 지정된 통보입니다");
        }

        long daysSinceSent = ChronoUnit.DAYS.between(log.getSentOn(), today);
        if (daysSinceSent < DESIGNATION_GRACE_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "2차 통보 후 10일 경과 후에만 강제 지정 가능합니다");
        }

        UUID companyLeaveTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCodeAndDelYn(companyId, DEFAULT_LEAVE_TYPE_CODE, "N")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "기본 연차 종류를 찾을 수 없습니다"));

        // 날짜별 LeaveRequest 생성 (휴일 주말 제외 정책은 추후 도입 가능)
        for (LocalDate date : reqDto.getDates()) {
            LeaveRequest req = LeaveRequest.createDesignated(
                    log.getMemberId(),
                    companyId,
                    companyLeaveTypeId,
                    date, date,
                    1.0,
                    reqDto.getReason(),
                    adminId);
            leaveRequestRepository.save(req);
        }

        // 잔여 차감
        for (LocalDate date : reqDto.getDates()) {
            MemberBalanceUseReqDto useReq = MemberBalanceUseReqDto.builder()
                    .balanceType(BalanceType.ANNUAL)
                    .days(1.0)
                    .leaveDate(date)
                    .build();
            memberBalanceService.useBalance(companyId, log.getMemberId(), useReq);
        }

        // 로그 업데이트
        log.designate(serializeDates(reqDto.getDates()), reqDto.getReason());

        // 직원 알림 발송
        publishDesignationNotice(log.getMemberId(), reqDto.getDates(), log.getMemberBalanceId());
    }

    /**
     * 2차 통보 시점 자동 강제 지정 (근로기준법 61조 정합)
     * 1차 통보 회신 없이 D-60 도달 -> 회사가 만료일 직전 평일 N일을 자동 지정
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoDesignateOnSecondNotice(UUID promotionLogId, UUID systemActorId) {

        LeavePromotionLog promotionLog = promotionLogRepository.findById(promotionLogId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "촉진 통보를 찾을 수 없습니다"));
        if (promotionLog.getStage() != PromotionStage.SECOND) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "2차 통보 건만 자동 지정 가능합니다");
        }
        if (promotionLog.getStatus() != PromotionLogStatus.SENT) {
            // 이미 회신/지정된 건은 스킵 (멱등성)
            return;
        }

        UUID companyId = promotionLog.getCompanyId();
        MemberBalance balance = memberBalanceRepository.findById(promotionLog.getMemberBalanceId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "잔고를 찾을 수 없습니다"));

        int remainingDays = (int) Math.floor(balance.getRemaining() != null ? balance.getRemaining() : 0.0);
        if (remainingDays <= 0) return;

        // 직원이 이월 동의한 잔고는 이월 가능 일수를 자동 지정 대상에서 제외
        if (balance.isCarryoverConsented()) {
            LeavePolicy policy = leavePolicyRepository
                    .findByCompanyIdAndDelYn(companyId, "N")
                    .stream().findFirst()
                    .orElse(null);
            if (policy != null && "Y".equals(policy.getIsCarryoverYn())
                    && policy.getCarryoverDays() != null && policy.getCarryoverDays() > 0) {
                int carryCap = policy.getCarryoverDays();
                int reduced = Math.max(0, remainingDays - Math.min(remainingDays, carryCap));
                log.info("[AutoDesignate] 이월 동의 반영 memberBalanceId={} 원래={}일 -> 지정대상={}일 (이월={}일)",
                        balance.getMemberBalanceId(), remainingDays, reduced, Math.min(remainingDays, carryCap));
                remainingDays = reduced;
                if (remainingDays <= 0) {
                    // 동의된 이월 일수가 잔여 전체와 같거나 더 큼 -> 자동 지정 불필요, 통보만 종료 처리
                    promotionLog.designate("[]", "이월 동의로 자동 지정 대상 없음");
                    return;
                }
            }
        }

        LocalDate expiration = balance.getExpirationDate();
        if (expiration == null) return;

        // 만료일부터 역순으로 평일 + 공휴일 아닌 날짜 N개 산출 (오늘 이후만)
        Set<LocalDate> holidays = companyHolidayRepository
                .findAllInRange(companyId, LocalDate.now(), expiration)
                .stream()
                .map(h -> h.getHolidayDate())
                .collect(Collectors.toSet());

        List<LocalDate> picked = pickDesignatedDates(expiration, remainingDays, holidays);
        if (picked.isEmpty()) {
            log.info("[AutoDesignate] 지정 가능 날짜 없음 - 스킵 memberId={} expiration={}",
                    promotionLog.getMemberId(), expiration);
            return;
        }

        UUID companyLeaveTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCodeAndDelYn(companyId, DEFAULT_LEAVE_TYPE_CODE, "N")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "기본 연차 종류를 찾을 수 없습니다"));

        String reason = "근로기준법 61조에 따른 회사 자동 지정 (1차 통보 후 미회신)";

        // requested_by 는 NOT NULL - 시스템 액터가 없으면 본인(직원) 명의
        UUID actor = systemActorId != null ? systemActorId : promotionLog.getMemberId();

        for (LocalDate date : picked) {
            LeaveRequest req = LeaveRequest.createDesignated(
                    promotionLog.getMemberId(),
                    companyId,
                    companyLeaveTypeId,
                    date, date,
                    1.0,
                    reason,
                    actor);
            leaveRequestRepository.save(req);
        }

        for (LocalDate date : picked) {
            MemberBalanceUseReqDto useReq = MemberBalanceUseReqDto.builder()
                    .balanceType(BalanceType.ANNUAL)
                    .days(1.0)
                    .leaveDate(date)
                    .build();
            memberBalanceService.useBalance(companyId, promotionLog.getMemberId(), useReq);
        }

        promotionLog.designate(serializeDates(picked), reason);
        publishDesignationNotice(promotionLog.getMemberId(), picked, promotionLog.getMemberBalanceId());
    }

    /** 만료일부터 역순으로 평일 + 공휴일 아닌 날짜 needed 개 추출 (오늘 이후만) */
    private List<LocalDate> pickDesignatedDates(LocalDate expiration, int needed,
                                                 Set<LocalDate> holidays) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = expiration;
        LocalDate today = LocalDate.now();
        int safetyLimit = 365; // 무한루프 방지
        while (result.size() < needed && cursor.isAfter(today) && safetyLimit-- > 0) {
            DayOfWeek dow = cursor.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            if (!isWeekend && !holidays.contains(cursor)) {
                result.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        Collections.sort(result); // 오름차순 정렬
        return result;
    }

    private void publishDesignationNotice(UUID memberId, List<LocalDate> dates,
                                          UUID memberBalanceId) {
        String content = "회사가 다음 날짜를 연차로 지정했습니다 (노무 수령 거부) "
                + dates;
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(memberId)
                .senderId(null)
                .notificationType(NotificationType.LEAVE_DESIGNATION)
                .content(content)
                .targetId(memberBalanceId)
                .targetType("MEMBER_BALANCE")
                .build());
    }

    private String serializeDates(List<LocalDate> dates) {
        try {
            return objectMapper.writeValueAsString(dates);
        } catch (JsonProcessingException e) {
            log.error("dates serialize fail", e);
            return "[]";
        }
    }

    private List<String> deserializeDateList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("dates deserialize fail json={}", json);
            return List.of();
        }
    }

    /**
     * 관리자 - 촉진 통보 이력 (회신 완료 + 강제 지정)
     *  - 회신완료 / 강제지정 모두 한 번에 조회 후 프론트에서 탭 분리
     */
    @Transactional(readOnly = true)
    public List<LeavePromotionHistoryResDto> findHistory(UUID companyId) {
        // 관리자 알림 현황 화면 - 발송된 모든 통보 노출
        List<LeavePromotionLog> sentLogs = promotionLogRepository
                .findByCompanyIdAndStatusOrderBySentOnDesc(companyId, PromotionLogStatus.SENT);
        List<LeavePromotionLog> ackLogs = promotionLogRepository
                .findByCompanyIdAndStatusOrderBySentOnDesc(companyId, PromotionLogStatus.ACKNOWLEDGED);
        List<LeavePromotionLog> desigLogs = promotionLogRepository
                .findByCompanyIdAndStatusOrderBySentOnDesc(companyId, PromotionLogStatus.DESIGNATED);

        List<LeavePromotionLog> all = new ArrayList<>();
        all.addAll(sentLogs);
        all.addAll(ackLogs);
        all.addAll(desigLogs);

        List<LeavePromotionHistoryResDto> result = new ArrayList<>();
        for (LeavePromotionLog log : all) {
            MemberBalance bal = memberBalanceRepository
                    .findById(log.getMemberBalanceId()).orElse(null);
            LocalDate expiry = bal != null ? bal.getExpirationDate() : null;
            Double remaining = bal != null ? bal.getRemaining() : null;

            result.add(LeavePromotionHistoryResDto.from(
                    log,
                    expiry,
                    remaining,
                    deserializeDateList(log.getPlannedDates()),
                    deserializeDateList(log.getDesignatedDates())));
        }
        result.sort(Comparator.comparing(LeavePromotionHistoryResDto::getSentOn).reversed());
        return result;
    }
}