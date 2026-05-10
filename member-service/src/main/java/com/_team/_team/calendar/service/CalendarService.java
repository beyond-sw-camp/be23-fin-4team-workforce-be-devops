package com._team._team.calendar.service;

import com._team._team.calendar.domain.CalendarEvent;
import com._team._team.calendar.domain.enums.EventType;
import com._team._team.calendar.dto.reqdto.CalendarEventCreateReqDto;
import com._team._team.calendar.dto.reqdto.CalendarEventUpdateReqDto;
import com._team._team.calendar.dto.resdto.CalendarEventResDto;
import com._team._team.calendar.dto.resdto.CalendarMonthlyResDto;
import com._team._team.calendar.dto.resdto.CompanyHolidayFeignDto;
import com._team._team.calendar.dto.resdto.LegalHolidayResDto;
import com._team._team.calendar.repository.CalendarEventRepository;
import com._team._team.company.feignclients.SalaryServiceClient;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.repository.MemberPositionRepository;
import com._team._team.member.repository.MemberRepository;
import com._team._team.notification.NotificationType;
import com._team._team.organization.domain.Organization;
import com._team._team.organization.repository.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final MemberRepository memberRepository;
    private final MemberPositionRepository memberPositionRepository;
    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LegalHolidayService legalHolidayService;
    private final SalaryServiceClient salaryServiceClient;

    @Autowired
    public CalendarService(CalendarEventRepository calendarEventRepository,
                           MemberRepository memberRepository,
                           MemberPositionRepository memberPositionRepository,
                           OrganizationRepository organizationRepository,
                           ApplicationEventPublisher eventPublisher,
                           LegalHolidayService legalHolidayService,
                           SalaryServiceClient salaryServiceClient) {
        this.calendarEventRepository = calendarEventRepository;
        this.memberRepository = memberRepository;
        this.memberPositionRepository = memberPositionRepository;
        this.organizationRepository = organizationRepository;
        this.eventPublisher = eventPublisher;
        this.legalHolidayService = legalHolidayService;
        this.salaryServiceClient = salaryServiceClient;
    }


    // 개인 일정 생성
    public UUID createPersonalEvent(UUID memberId, CalendarEventCreateReqDto reqDto) {
        Member member = getMember(memberId);

        validateDate(reqDto.getStartAt(), reqDto.getEndAt());

        CalendarEvent event = CalendarEvent.builder()
                .company(member.getCompany())
                .member(member)
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .startAt(reqDto.getStartAt())
                .endAt(reqDto.getEndAt())
                .eventType(EventType.PERSONAL)
                .isPublicYn(reqDto.getIsPublicYn())
                .build();

        return calendarEventRepository.save(event).getCalendarEventId();
    }

    // 개인 일정 수정 (본인만)
    public void updatePersonalEvent(UUID memberId, UUID eventId,
                                    CalendarEventUpdateReqDto reqDto) {
        CalendarEvent event = getEvent(eventId);

        if (event.getEventType() == EventType.APPROVAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "결재 연동 일정은 수정/삭제할 수 없습니다.");
        }

        validateOwner(event, memberId);
        validateEventType(event, EventType.PERSONAL);
        validateDate(reqDto.getStartAt(), reqDto.getEndAt());

        event.update(
                reqDto.getTitle(),
                reqDto.getDescription(),
                reqDto.getStartAt(),
                reqDto.getEndAt(),
                reqDto.getIsPublicYn()
        );
    }

    // 개인 일정 삭제 (본인만)
    public void deletePersonalEvent(UUID memberId, UUID eventId) {
        CalendarEvent event = getEvent(eventId);

        if (event.getEventType() == EventType.APPROVAL) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "결재 연동 일정은 수정/삭제할 수 없습니다.");
        }

        validateOwner(event, memberId);
        validateEventType(event, EventType.PERSONAL);

        event.delete();
    }

    // ═══════════════════════════════════════════════════════════
    // 팀 일정
    // ═══════════════════════════════════════════════════════════

    // 팀 일정 생성 (팀장, 인사 관리자)
    public UUID createTeamEvent(UUID memberId, CalendarEventCreateReqDto reqDto) {
        Member member = getMember(memberId);

        validateDate(reqDto.getStartAt(), reqDto.getEndAt());
        Organization organization = validateTeamOrganization(member, reqDto.getOrganizationId());

        CalendarEvent event = CalendarEvent.builder()
                .company(member.getCompany())
                .member(member)
                .organizationId(organization.getOrganizationId())
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .startAt(reqDto.getStartAt())
                .endAt(reqDto.getEndAt())
                .eventType(EventType.TEAM)
                .isPublicYn("YES")  // 팀 일정은 항상 공개
                .build();

        // 같은 조직 직원 전체에게 알림
        memberPositionRepository
                .findByOrganization_OrganizationIdAndDelYn(organization.getOrganizationId(), "NO")
                .forEach(position -> {
                    if (!position.getMember().getMemberId().equals(memberId)) {
                        eventPublisher.publishEvent(NotificationMessage.builder()
                                .receiverId(position.getMember().getMemberId())
                                .senderId(memberId)
                                .notificationType(NotificationType.CALENDAR_TEAM_EVENT_CREATED)
                                .content("팀 일정이 등록됐습니다. " + reqDto.getTitle()
                                        + " (" + reqDto.getStartAt().toLocalDate() + ")")
                                .targetId(event.getCalendarEventId())
                                .targetType("CALENDAR_EVENT")
                                .build());
                    }
                });

        return calendarEventRepository.save(event).getCalendarEventId();
    }

    // 팀 일정 수정 (생성자만)
    public void updateTeamEvent(UUID memberId, UUID eventId,
                                CalendarEventUpdateReqDto reqDto) {
        CalendarEvent event = getEvent(eventId);

        validateOwner(event, memberId);
        validateEventType(event, EventType.TEAM);
        validateDate(reqDto.getStartAt(), reqDto.getEndAt());

        event.update(
                reqDto.getTitle(),
                reqDto.getDescription(),
                reqDto.getStartAt(),
                reqDto.getEndAt(),
                "YES"  // 팀 일정은 항상 공개
        );
        memberPositionRepository
                .findByOrganization_OrganizationIdAndDelYn(
                        event.getOrganizationId(), "NO")
                .forEach(position -> {
                    if (!position.getMember().getMemberId().equals(memberId)) {
                        eventPublisher.publishEvent(NotificationMessage.builder()
                                .receiverId(position.getMember().getMemberId())
                                .senderId(memberId)
                                .notificationType(NotificationType.CALENDAR_TEAM_EVENT_CREATED)
                                .content("팀 일정이 수정됐습니다. [" + reqDto.getTitle() + "] "
                                        + reqDto.getStartAt().toLocalDate())
                                .targetId(event.getCalendarEventId())
                                .targetType("CALENDAR_EVENT")
                                .build());
                    }
                });
    }

    // 팀 일정 삭제 (생성자만)
    public void deleteTeamEvent(UUID memberId, UUID eventId) {
        CalendarEvent event = getEvent(eventId);

        validateOwner(event, memberId);
        validateEventType(event, EventType.TEAM);

        event.delete();
    }


    // 일별 조회
    @Transactional(readOnly = true)
    public List<CalendarEventResDto> getDailyEvents(UUID memberId,
                                                    LocalDate date,
                                                    EventType eventType) {
        Member member = getMember(memberId);

        LocalDateTime startAt = date.atStartOfDay();
        LocalDateTime endAt = date.atTime(23, 59, 59);

        return getEvents(member, startAt, endAt, eventType);
    }

    // 주간별 조회
    @Transactional(readOnly = true)
    public List<CalendarEventResDto> getWeeklyEvents(UUID memberId,
                                                     LocalDate date,
                                                     EventType eventType) {
        Member member = getMember(memberId);

        // 해당 날짜가 속한 주의 월요일 ~ 일요일
        LocalDateTime startAt = date.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endAt = date.with(DayOfWeek.SUNDAY).atTime(23, 59, 59);

        return getEvents(member, startAt, endAt, eventType);
    }

    // 월별 조회
    @Transactional(readOnly = true)
    public CalendarMonthlyResDto getMonthlyEvents(UUID memberId,
                                                  UUID companyId,
                                                  int year,
                                                  int month,
                                                  EventType eventType) {
        Member member = getMember(memberId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startAt = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endAt = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        List<CalendarEventResDto> events = getEvents(member, startAt, endAt, eventType);

        return CalendarMonthlyResDto.builder()
                .events(events)
                .holidays(loadHolidaysForMonth(companyId, year, month))
                .build();
    }

    // 대시보드용 다가오는 일정 조회
    @Transactional(readOnly = true)
    public List<CalendarEventResDto> getUpcomingEvents(UUID memberId,
                                                       LocalDate from,
                                                       int limit,
                                                       EventType eventType) {
        Member member = getMember(memberId);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        LocalDateTime startAt = from.atStartOfDay();
        LocalDateTime endAt = from.plusMonths(3).atTime(23, 59, 59);

        return fetchEvents(member, startAt, endAt, eventType)
                .stream()
                .filter(event -> !event.getStartAt().toLocalDate().isBefore(from))
                .sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
                .limit(safeLimit)
                .map(CalendarEventResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 법정 공휴일 + 회사 휴일 합쳐서 월 범위로 필터, 같은 날짜는 회사 휴일이 우선
    private List<CalendarMonthlyResDto.HolidayDto> loadHolidaysForMonth(UUID companyId, int year, int month) {
        LocalDate from = YearMonth.of(year, month).atDay(1);
        LocalDate to = YearMonth.of(year, month).atEndOfMonth();

        // 날짜 -> 이름
        Map<LocalDate, String> merged = new LinkedHashMap<>();

        // 1. 법정 공휴일
        try {
            List<LegalHolidayResDto> legal = legalHolidayService.getHolidays(year);
            for (LegalHolidayResDto h : legal) {
                LocalDate d = h.getHolidayDate();
                if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                    merged.put(d, h.getHolidayName());
                }
            }
        } catch (Exception e) {
            log.warn("법정 공휴일 조회 실패 year={} err={}", year, e.getMessage());
        }

        // 2. 회사 휴일
        try {
            ApiResponse<List<CompanyHolidayFeignDto>> resp = salaryServiceClient.findCompanyHolidays(companyId);
            List<CompanyHolidayFeignDto> companyHolidays = resp != null ? resp.getData() : null;
            if (companyHolidays != null) {
                for (CompanyHolidayFeignDto h : companyHolidays) {
                    LocalDate d = h.getHolidayDate();
                    if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                        merged.put(d, h.getHolidayName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("회사 휴일 조회 실패 companyId={} err={}", companyId, e.getMessage());
        }

        return merged.entrySet().stream()
                .map(e -> CalendarMonthlyResDto.HolidayDto.builder()
                        .holidayDate(e.getKey())
                        .holidayName(e.getValue())
                        .build())
                .sorted((a, b) -> a.getHolidayDate().compareTo(b.getHolidayDate()))
                .collect(Collectors.toList());
    }

    // 공통 조회 로직
    private List<CalendarEvent> fetchEvents(Member member,
                                            LocalDateTime startAt,
                                            LocalDateTime endAt,
                                            EventType eventType) {
        List<CalendarEvent> events = new ArrayList<>();

        if (eventType == null || eventType == EventType.PERSONAL) {
            events.addAll(calendarEventRepository
                    .findPersonalByMonth(member, startAt, endAt));
        }

        if (eventType == null || eventType == EventType.TEAM) {
            MemberPosition position = memberPositionRepository
                    .findById(member.getDefaultPositionId())
                    .orElse(null);

            if (position != null) {
                events.addAll(calendarEventRepository.findTeamByMonth(
                        position.getOrganization().getOrganizationId(),
                        startAt, endAt));
            }
        }

        if (eventType == null || eventType == EventType.APPROVAL) {
            MemberPosition position = memberPositionRepository
                    .findById(member.getDefaultPositionId())
                    .orElse(null);

            if (position != null) {
                events.addAll(calendarEventRepository.findApprovalByMonth(
                        member,
                        position.getOrganization().getOrganizationId(),
                        startAt, endAt));
            }
        }

        return events;
    }

    private List<CalendarEventResDto> getEvents(Member member,
                                                LocalDateTime startAt,
                                                LocalDateTime endAt,
                                                EventType eventType) {
        return fetchEvents(member, startAt, endAt, eventType)
                .stream()
                .sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
                .map(CalendarEventResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 일정 상세 조회
    @Transactional(readOnly = true)
    public CalendarEventResDto getEventDetail(UUID memberId, UUID eventId) {
        Member member = getMember(memberId);
        CalendarEvent event = getEvent(eventId);

        // 다른 회사 일정 조회 불가
        if (!event.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근할 수 없는 일정입니다.");
        }

        // 비공개 개인 일정은 본인만 조회 가능
        if ("NO".equals(event.getIsPublicYn())
                && !event.getMember().getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "비공개 일정입니다.");
        }

        return CalendarEventResDto.fromEntity(event);
    }

    // Private 헬퍼

    private Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
    }

    private CalendarEvent getEvent(UUID eventId) {
        return calendarEventRepository
                .findByCalendarEventIdAndDelYn(eventId, "NO")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 일정입니다."));
    }

    private void validateDate(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "종료일시는 시작일시보다 이후여야 합니다.");
        }
    }

    /**
     * TEAM 일정은 유효한 하위 조직(루트 제외)으로만 생성 가능.
     */
    private Organization validateTeamOrganization(Member member, UUID organizationId) {
        if (organizationId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "팀 일정은 organizationId가 필수입니다.");
        }
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "유효하지 않은 organizationId입니다."));
        if (!"NO".equals(org.getDelYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "삭제된 조직에는 팀 일정을 생성할 수 없습니다.");
        }
        if (org.getParent() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "루트 조직(전사)에는 팀 일정을 생성할 수 없습니다.");
        }
        if (!org.getCompany().getCompanyId().equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "다른 회사 조직에는 팀 일정을 생성할 수 없습니다.");
        }
        return org;
    }

    private void validateOwner(CalendarEvent event, UUID memberId) {
        if (!event.getMember().getMemberId().equals(memberId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "본인이 생성한 일정만 수정/삭제할 수 있습니다.");
        }
    }

    private void validateEventType(CalendarEvent event, EventType expectedType) {
        if (event.getEventType() != expectedType) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "일정 유형이 올바르지 않습니다.");
        }
    }
}
