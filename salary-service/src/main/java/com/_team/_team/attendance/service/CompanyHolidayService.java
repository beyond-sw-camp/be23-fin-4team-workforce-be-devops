package com._team._team.attendance.service;

import com._team._team.attendance.domain.CompanyHoliday;
import com._team._team.attendance.dto.reqDto.CompanyHolidayCreateReqDto;
import com._team._team.attendance.dto.reqDto.CompanyHolidayUpdateReqDto;
import com._team._team.attendance.dto.resDto.CompanyHolidayResDto;
import com._team._team.attendance.feignClients.MemberCalendarClient;
import com._team._team.attendance.publisher.RagSyncAttendanceEventPublisher;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.dto.BusinessException;
import com._team._team.attendance.dto.resDto.LegalHolidayResDto;
import com._team._team.event.RagSyncAttendanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class CompanyHolidayService {

    private final CompanyHolidayRepository companyHolidayRepository;
    private final MemberCalendarClient memberCalendarClient;
    private final RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher;

    @Autowired
    public CompanyHolidayService(CompanyHolidayRepository companyHolidayRepository,
                                 MemberCalendarClient memberCalendarClient,
                                 RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher) {
        this.companyHolidayRepository = companyHolidayRepository;
        this.memberCalendarClient = memberCalendarClient;
        this.ragSyncAttendanceEventPublisher = ragSyncAttendanceEventPublisher;
    }

    /**
     * 공휴일 등록
     */
    @CacheEvict(value = "companyHolidays", key = "#companyId")
    public CompanyHolidayResDto createHoliday(UUID companyId, CompanyHolidayCreateReqDto reqDto) {
        boolean duplicated = companyHolidayRepository
                .existsByCompanyIdAndHolidayDateAndDelYn(
                        companyId, reqDto.getHolidayDate(), "N");
        if (duplicated) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이미 등록된 공휴일입니다: " + reqDto.getHolidayDate());
        }

        CompanyHoliday holiday = reqDto.toEntity(companyId);
        CompanyHoliday saved = companyHolidayRepository.save(holiday);

        // RAG 동기화 이벤트 발행
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("HOLIDAY")
                        .resourceId(saved.getCompanyHolidayId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return CompanyHolidayResDto.fromEntity(saved);
    }

    /** 공휴일 전체 목록 조회 (캐시 일시 비활성) */
    @Transactional(readOnly = true)
    public List<CompanyHolidayResDto> findHolidays(UUID companyId) {
        List<CompanyHoliday> holidays = companyHolidayRepository
                .findByCompanyIdAndDelYnOrderByHolidayDate(companyId, "N");
        return holidays.stream()
                .map(CompanyHolidayResDto::fromEntity)
                .toList();
    }

    /**
     * 공휴일 수정 (커스텀만)  // 메서드 실행 후 지정된 캐시에서 특정 키의 항목을 삭제 어노테이션 추가
     */
    @CacheEvict(value = "companyHolidays", key = "#companyId")
    public CompanyHolidayResDto updateHoliday(UUID companyId, UUID companyHolidayId, CompanyHolidayUpdateReqDto reqDto) {
        CompanyHoliday holiday = findActive(companyId, companyHolidayId);

        if ("Y".equals(holiday.getIsLegalYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "법정 공휴일은 수정할 수 없습니다.");
        }

        holiday.update(reqDto);

        // RAG 동기화 이벤트 발행
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("HOLIDAY")
                        .resourceId(companyHolidayId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return CompanyHolidayResDto.fromEntity(holiday);
    }

    /**
     * 공휴일 삭제 (커스텀만) // 메서드 실행 후 지정된 캐시에서 특정 키의 항목을 삭제 어노테이션 추가
     */
    @CacheEvict(value = "companyHolidays", key = "#companyId")
    public void deleteHoliday(UUID companyId, UUID companyHolidayId) {
        CompanyHoliday holiday = findActive(companyId, companyHolidayId);

        if ("Y".equals(holiday.getIsLegalYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "법정 공휴일은 삭제할 수 없습니다. 필요시 새로고침으로 재수집하세요.");
        }
        holiday.delete();

        // RAG 동기화 이벤트 발행
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("HOLIDAY")
                        .resourceId(companyHolidayId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    /**
     * 회사 가입 시 법정 공휴일 일괄 복사, 올해 + 내년
     */
    public void importPublicHolidays(UUID companyId) {
        int currentYear = LocalDate.now().getYear();
        importYear(companyId, currentYear);
        importYear(companyId, currentYear + 1);

        // RAG 동기화 이벤트 발행 (BULK)
        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("BULK")
                        .resourceType("HOLIDAY")
                        .resourceId(null)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    /**
     * 관리자 수동 새로고침
     */
    public int refreshLegalHolidays(UUID companyId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        // 1. 해당 연도 법정 공휴일 하드 삭제
        List<CompanyHoliday> existing = companyHolidayRepository
                .findByCompanyIdAndIsLegalYnAndHolidayDateBetween(
                        companyId, "Y", yearStart, yearEnd);
        companyHolidayRepository.deleteAll(existing);

        // 2. 최신 데이터(공휴일) 조회
        List<LegalHolidayResDto> holidays;
        try {
            holidays = memberCalendarClient.getHolidays(year).getData();
        } catch (Exception e) {
            log.error("법정 공휴일 조회 실패 year={}", year, e);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "공휴일 API 호출에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        if (holidays == null || holidays.isEmpty()) {
            log.warn("공공 API 에 {}년 공휴일 데이터 없음", year);
            return 0;
        }

        // 3. CompanyHoliday 로 재삽입
        List<CompanyHoliday> toSave = holidays.stream()
                .map(h -> CompanyHoliday.builder()
                        .companyId(companyId)
                        .holidayDate(h.getHolidayDate())
                        .holidayName(h.getHolidayName())
                        .isPaidYn("Y")
                        .isLegalYn("Y")
                        .build())
                .toList();

        companyHolidayRepository.saveAll(toSave);
        log.info("법정 공휴일 새로고침 완료 companyId={}, year={}, count={}",
                companyId, year, toSave.size());

        // RAG 동기화 이벤트 발행 (BULK, 실제 저장된 경우만)
        if (!toSave.isEmpty()) {
            ragSyncAttendanceEventPublisher.publish(
                    RagSyncAttendanceEvent.builder()
                            .eventId(UUID.randomUUID())
                            .companyId(companyId)
                            .action("BULK")
                            .resourceType("HOLIDAY")
                            .resourceId(null)
                            .timestamp(Instant.now())
                            .triggeredBy("system")
                            .build()
            );
        }

        return toSave.size();
    }

    private void importYear(UUID companyId, int year) {
        try {
            List<LegalHolidayResDto> holidays = memberCalendarClient
                    .getHolidays(year).getData();

            if (holidays == null) return;

            List<CompanyHoliday> toSave = holidays.stream()
                    .filter(h -> !exists(companyId, h.getHolidayDate()))
                    .map(h -> CompanyHoliday.builder()
                            .companyId(companyId)
                            .holidayDate(h.getHolidayDate())
                            .holidayName(h.getHolidayName())
                            .isPaidYn("Y")
                            .isLegalYn("Y")
                            .build())
                    .toList();

            if (!toSave.isEmpty()) {
                companyHolidayRepository.saveAll(toSave);
            }
        } catch (Exception e) {
            log.warn("법정 공휴일 복사 실패 companyId={}, year={}, error={}",
                    companyId, year, e.getMessage());
        }
    }

    private boolean exists(UUID companyId, LocalDate date) {
        return companyHolidayRepository
                .existsByCompanyIdAndHolidayDateAndDelYn(companyId, date, "N");
    }

    private CompanyHoliday findActive(UUID companyId, UUID companyHolidayId) {
        return companyHolidayRepository
                .findByCompanyIdAndCompanyHolidayIdAndDelYn(
                        companyId, companyHolidayId, "N")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "공휴일을 찾을 수 없습니다."));
    }
}