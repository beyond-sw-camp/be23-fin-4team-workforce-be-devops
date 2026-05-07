package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.FlexibleTimeSlotCreateReqDto;
import com._team._team.attendance.dto.reqDto.FlexibleTimeSlotUpdateReqDto;
import com._team._team.attendance.dto.resDto.FlexibleTimeSlotResDto;
import com._team._team.attendance.service.FlexibleTimeSlotService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 시차출퇴근 스케줄 관리 API (관리자 전용)
 */
@RestController
@RequestMapping("/attendance/flexible-slots")
public class FlexibleTimeSlotController {

    private final FlexibleTimeSlotService flexibleTimeSlotService;

    @Autowired
    public FlexibleTimeSlotController(FlexibleTimeSlotService flexibleTimeSlotService) {
        this.flexibleTimeSlotService = flexibleTimeSlotService;
    }

    /**
     * 스케줄 생성
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody FlexibleTimeSlotCreateReqDto reqDto) {

        FlexibleTimeSlotResDto resDto = flexibleTimeSlotService.create(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 슬롯이 생성되었습니다."),
                HttpStatus.CREATED);
    }

    /**
     * 스케줄 수정
     */
    @PutMapping("/{slotId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID slotId,
            @Valid @RequestBody FlexibleTimeSlotUpdateReqDto reqDto) {

        FlexibleTimeSlotResDto resDto = flexibleTimeSlotService.update(slotId, companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 슬롯이 수정되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 스케줄 폐지 (소프트 삭제)
     */
    @DeleteMapping("/{slotId}")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID slotId) {

        flexibleTimeSlotService.deactivate(slotId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "스케줄 슬롯이 폐지되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 기본 스케줄 지정
     * 기본 스케줄 자동으로 해제됨
     */
    @PutMapping("/{slotId}/default")
    public ResponseEntity<?> setAsDefault(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID slotId) {

        FlexibleTimeSlotResDto resDto = flexibleTimeSlotService.setAsDefault(slotId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "기본 슬롯으로 지정되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 스케줄 단건 조회
     */
    @GetMapping("/{slotId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID slotId) {

        FlexibleTimeSlotResDto resDto = flexibleTimeSlotService.findById(slotId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 슬롯 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 특정 스케줄의 활성 목록
     * 직원 스케줄 선택 UI 에서 노출할 옵션 조회
     */
    @GetMapping
    public ResponseEntity<?> findActiveByWorkSchedule(
            @RequestParam UUID workScheduleId) {

        List<FlexibleTimeSlotResDto> resDto =
                flexibleTimeSlotService.findActiveByWorkSchedule(workScheduleId);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 슬롯 목록 조회 성공"),
                HttpStatus.OK);
    }
}