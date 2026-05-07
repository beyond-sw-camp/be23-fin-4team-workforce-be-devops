package com._team._team.calendar.contoller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.calendar.domain.enums.EventType;
import com._team._team.calendar.dto.reqdto.CalendarEventCreateReqDto;
import com._team._team.calendar.dto.reqdto.CalendarEventUpdateReqDto;
import com._team._team.calendar.service.CalendarService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    @Autowired
    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * 개인 일정 생성
     * - 모든 직원 접근 가능
     */
    @PostMapping("/personal")
    public ResponseEntity<?> createPersonalEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody CalendarEventCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.createPersonalEvent(memberId, reqDto),
                        "개인 일정 생성 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 개인 일정 수정
     * - 본인 일정만 수정 가능
     */
    @PutMapping("/personal/{eventId}")
    public ResponseEntity<?> updatePersonalEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID eventId,
            @Valid @RequestBody CalendarEventUpdateReqDto reqDto) {
        calendarService.updatePersonalEvent(memberId, eventId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "개인 일정 수정 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 개인 일정 삭제
     * - 본인 일정만 삭제 가능
     * - 소프트 삭제
     */
    @DeleteMapping("/personal/{eventId}")
    public ResponseEntity<?> deletePersonalEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID eventId) {
        calendarService.deletePersonalEvent(memberId, eventId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "개인 일정 삭제 성공"),
                HttpStatus.OK
        );
    }


    /**
     * 팀 일정 생성
     * - CALENDAR CREATE 권한 필요 (팀장, 인사 관리자)
     * - organizationId 필수
     */
    @CheckPermission(resource = Resource.CALENDAR, action = Action.CREATE)
    @PostMapping("/team")
    public ResponseEntity<?> createTeamEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody CalendarEventCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.createTeamEvent(memberId, reqDto),
                        "팀 일정 생성 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 팀 일정 수정
     * - CALENDAR UPDATE 권한 필요 (팀장, 인사 관리자)
     * - 생성자만 수정 가능
     */
    @CheckPermission(resource = Resource.CALENDAR, action = Action.UPDATE)
    @PutMapping("/team/{eventId}")
    public ResponseEntity<?> updateTeamEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID eventId,
            @Valid @RequestBody CalendarEventUpdateReqDto reqDto) {
        calendarService.updateTeamEvent(memberId, eventId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "팀 일정 수정 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 팀 일정 삭제
     * - CALENDAR DELETE 권한 필요 (팀장, 인사 관리자)
     * - 생성자만 삭제 가능
     */
    @CheckPermission(resource = Resource.CALENDAR, action = Action.DELETE)
    @DeleteMapping("/team/{eventId}")
    public ResponseEntity<?> deleteTeamEvent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID eventId) {
        calendarService.deleteTeamEvent(memberId, eventId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "팀 일정 삭제 성공"),
                HttpStatus.OK
        );
    }


    /**
     * 일별 일정 조회
     * GET /calendar/daily?date=2026-04-07&eventType=PERSONAL
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDailyEvents(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam LocalDate date,
            @RequestParam(required = false) EventType eventType) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.getDailyEvents(memberId, date, eventType),
                        "일별 일정 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 주간별 일정 조회
     * GET /calendar/weekly?date=2026-04-07&eventType=TEAM
     * date 가 속한 주의 월~일 조회
     */
    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklyEvents(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam LocalDate date,
            @RequestParam(required = false) EventType eventType) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.getWeeklyEvents(memberId, date, eventType),
                        "주간별 일정 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 월별 일정 조회
     * GET /calendar?year=2026&month=4&eventType=PERSONAL
     */
    @GetMapping
    public ResponseEntity<?> getMonthlyEvents(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) EventType eventType) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.getMonthlyEvents(memberId, companyId,year, month, eventType),
                        "월별 일정 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 일정 상세 조회
     * - 비공개 개인 일정은 본인만 조회 가능
     * - 다른 회사 일정 조회 불가
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEventDetail(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID eventId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        calendarService.getEventDetail(memberId, eventId),
                        "일정 상세 조회 성공"),
                HttpStatus.OK
        );
    }
}