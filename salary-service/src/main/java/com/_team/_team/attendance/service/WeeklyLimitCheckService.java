package com._team._team.attendance.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.dto.vo.LaborLawViolation;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.service.CachedMemberLookupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 주 52시간 초과 감지 서비스
 * 어제 근무 기록 있는 직원 대상으로 주간 한도 검증 후 알림 발송
 */
@Slf4j
@Service
public class WeeklyLimitCheckService {

    private static final String TARGET_TYPE = "WEEKLY_LIMIT_CHECK";

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final LaborLawValidator laborLawValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberFeignClient memberFeignClient;
    private final CachedMemberLookupService cachedMemberLookup;

    @Autowired
    public WeeklyLimitCheckService(DailyAttendanceRepository dailyAttendanceRepository,
                                   LaborLawValidator laborLawValidator,
                                   ApplicationEventPublisher eventPublisher,
                                   MemberFeignClient memberFeignClient,
                                   CachedMemberLookupService cachedMemberLookup) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.laborLawValidator = laborLawValidator;
        this.eventPublisher = eventPublisher;
        this.memberFeignClient = memberFeignClient;
        this.cachedMemberLookup = cachedMemberLookup;
    }

    // 특정 일자 기준으로 해당 주의 근로시간 한도 직원 감지 + 알림
    @Transactional(readOnly = true)
    public CheckResult checkForDate(LocalDate targetDate) {
        List<DailyAttendance> attendanceList = dailyAttendanceRepository
                .findAllByAttendanceDate(targetDate);

        int checked = 0;
        int violated = 0;

        for (DailyAttendance da : attendanceList) {
            List<LaborLawViolation> violations = laborLawValidator
                    .validateWeeklyLimit(da.getMemberId(), da.getCompanyId(), targetDate);
            checked++;

            if (!violations.isEmpty()) {
                publishViolationNotice(da, violations);
                violated++;
            }
        }

        log.info("[WeeklyLimitCheck] targetDate={} 검사={} 위반={}",
                targetDate, checked, violated);

        return new CheckResult(checked, violated);
    }

    // 위반 직원에게 알림 발송 + 회사 시스템 관리자 전체에게 같은 알림 발송
    private void publishViolationNotice(DailyAttendance da, List<LaborLawViolation> violations) {
        LaborLawViolation first = violations.get(0);

        // 1) 직원 본인 알림
        String employeeContent = String.format(
                "주간 근로시간 한도를 초과했습니다. %s",
                first.message());
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(da.getMemberId())
                .senderId(null)
                .notificationType(NotificationType.LABOR_LAW_WEEKLY_VIOLATION)
                .content(employeeContent)
                .targetId(da.getDailyAttendanceId())
                .targetType(TARGET_TYPE)
                .build());

        // 2) 회사 시스템 관리자 전체에게 알림 (인사팀 모니터링 용)
        try {
            // 위반자 정보 (이름/부서)
            String violatorLabel = resolveMemberLabel(da.getCompanyId(), da.getMemberId());

            ApiResponse<List<MemberResDto>> apiRes = memberFeignClient
                    .getAdminsByCompany(da.getCompanyId());
            List<MemberResDto> admins = apiRes != null && apiRes.getData() != null
                    ? apiRes.getData() : List.of();
            String adminContent = String.format(
                    "[근로기준법 알림] %s 주간 근로시간 한도 초과 - %s",
                    violatorLabel, first.message());
            for (MemberResDto admin : admins) {
                UUID adminId = admin.getMemberId();
                if (adminId == null || adminId.equals(da.getMemberId())) continue;
                eventPublisher.publishEvent(NotificationMessage.builder()
                        .receiverId(adminId)
                        .senderId(null)
                        .notificationType(NotificationType.LABOR_LAW_WEEKLY_VIOLATION)
                        .content(adminContent)
                        .targetId(da.getDailyAttendanceId())
                        .targetType(TARGET_TYPE)
                        .build());
            }
        } catch (Exception e) {
            log.warn("[WeeklyLimitCheck] 관리자 알림 발송 실패 - {}", e.getMessage());
        }
    }

    /** 부서명 직원이름 */
    private String resolveMemberLabel(UUID companyId, UUID memberId) {
        try {
            List<MemberResDto> members = cachedMemberLookup.getMembersByCompany(companyId);
            if (members != null) {
                for (MemberResDto m : members) {
                    if (memberId.equals(m.getMemberId())) {
                        String name = m.getName() != null ? m.getName() : "";
                        String dept = m.getOrganizationName() != null ? m.getOrganizationName() : "";
                        if (!dept.isBlank() && !name.isBlank()) return dept + " " + name;
                        if (!name.isBlank()) return name;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WeeklyLimitCheck] 직원 라벨 조회 실패 - {}", e.getMessage());
        }
        return "직원(" + memberId.toString().substring(0, 8) + ")";
    }

    // 결과 요약 record
    public record CheckResult(int checked, int violated) {}
}
