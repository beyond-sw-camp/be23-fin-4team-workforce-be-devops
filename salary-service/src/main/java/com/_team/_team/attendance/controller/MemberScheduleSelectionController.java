package com._team._team.attendance.controller;

import com._team._team.attendance.dto.reqDto.MemberScheduleSelectionReqDto;
import com._team._team.attendance.dto.resDto.MemberScheduleSelectionResDto;
import com._team._team.attendance.service.MemberScheduleSelectionService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 시차출퇴근 스케줄 선택
 */
@RestController
@RequestMapping("/attendance/schedule-selections")
public class MemberScheduleSelectionController {

    private final MemberScheduleSelectionService memberScheduleSelectionService;

    @Autowired
    public MemberScheduleSelectionController(
            MemberScheduleSelectionService memberScheduleSelectionService) {
        this.memberScheduleSelectionService = memberScheduleSelectionService;
    }

    /**
     * 스케줄 선택 제출 (PENDING)
     * 최초 선택과 월 중 변경 요청 모두 동일
     */
    @PostMapping("/my")
    public ResponseEntity<?> submit(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody MemberScheduleSelectionReqDto reqDto) {

        MemberScheduleSelectionResDto resDto =
                memberScheduleSelectionService.submit(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "스케줄 슬롯 선택이 제출되었습니다."),
                HttpStatus.CREATED);
    }

    /**
     * 본인 선택 취소 (PENDING 만 가능)
     */
    @DeleteMapping("/my/{selectionId}")
    public ResponseEntity<?> cancel(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID selectionId) {

        memberScheduleSelectionService.cancel(selectionId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "스케줄 슬롯 선택이 취소되었습니다."),
                HttpStatus.OK);
    }

    /**
     * 내 현재 적용 스케줄 조회
     */
    @GetMapping("/my/current")
    public ResponseEntity<?> findMyCurrent(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam String yearMonth) {

        MemberScheduleSelectionResDto resDto =
                memberScheduleSelectionService.findMyCurrent(memberId, yearMonth);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "현재 스케줄 슬롯 조회 성공"),
                HttpStatus.OK);
    }

    /**
     * 내 해당 월 이력 전체 조회
     * PENDING, REJECTED, CANCELLED 포함
     */
    @GetMapping("/my/history")
    public ResponseEntity<?> findMyHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam String yearMonth) {

        List<MemberScheduleSelectionResDto> resDto =
                memberScheduleSelectionService.findMyHistory(memberId, yearMonth);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "이력 조회 성공"),
                HttpStatus.OK);
    }
}