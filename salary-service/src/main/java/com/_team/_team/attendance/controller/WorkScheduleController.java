package com._team._team.attendance.controller;

import com._team._team.attendance.service.WorkScheduleService;
import com._team._team.attendance.dto.reqDto.WorkScheduleCreateReqDto;
import com._team._team.attendance.dto.reqDto.WorkScheduleUpdateReqDto;
import com._team._team.attendance.dto.resDto.WorkScheduleResDto;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 근무 스케줄 Controller
 * - 회사별 근무 스케줄 CRUD (FIXED/FLEXIBLE/SHIFT)
 * - memberId가 null이면 회사 기본 스케줄, 값이 있으면 개인별 스케줄
 */
@RestController
@RequestMapping("/work-schedules")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @Autowired
    public WorkScheduleController(WorkScheduleService workScheduleService) {
        this.workScheduleService = workScheduleService;
    }

    /**
     * 스케줄 생성
     * - 회사 기본 스케줄: memberId를 null로 전달
     * - 개인별 스케줄: memberId를 포함하여 전달
     */
    @PostMapping("/create")
    public ResponseEntity<?> createSchedule(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody WorkScheduleCreateReqDto reqDto) {
        WorkScheduleResDto resDto = workScheduleService.createSchedule(companyId, reqDto);
        return new ResponseEntity<> (
                ApiResponse.success(resDto, "스케줄이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 스케줄 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findSchedules(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<WorkScheduleResDto> resDtoList = workScheduleService.findSchedules(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "스케줄 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 스케줄 단건 조회 */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<?> findSchedule(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID scheduleId) {
        WorkScheduleResDto resDto = workScheduleService.findSchedule(companyId, scheduleId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 스케줄 수정 */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<?> updateSchedule(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody WorkScheduleUpdateReqDto reqDto) {
        WorkScheduleResDto resDto = workScheduleService.updateSchedule(companyId, scheduleId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 스케줄 삭제
     * - delYn을 'Y'로 변경
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID scheduleId) {
        workScheduleService.deleteSchedule(companyId, scheduleId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "스케줄이 삭제되었습니다."),
                HttpStatus.OK
        );
    }
    // 내부 통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findSchedulesInternal(@RequestParam UUID companyId) {
        List<WorkScheduleResDto> resDtoList = workScheduleService.findSchedules(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "스케줄 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
