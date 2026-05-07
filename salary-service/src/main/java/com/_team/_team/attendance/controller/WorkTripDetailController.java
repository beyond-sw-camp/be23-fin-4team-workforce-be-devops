package com._team._team.attendance.controller;

import com._team._team.attendance.service.WorkTripDetailService;
import com._team._team.attendance.dto.reqDto.WorkTripCreateReqDto;
import com._team._team.attendance.dto.reqDto.WorkTripUpdateReqDto;
import com._team._team.attendance.dto.resDto.WorkTripResDto;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


/**
 * [사원용]
 * POST   /work-trip                        출장/외근 등록
 * GET    /work-trip                        내 출장/외근 이력 조회
 * GET    /work-trip/daily/{dailyAttendanceId}  특정일 출장/외근 내역
 * PUT    /work-trip/{workTripDetailId}      출장/외근 수정
 * DELETE /work-trip/{workTripDetailId}      출장/외근 삭제
 */
@RestController
@RequestMapping("/work-trip")
public class WorkTripDetailController {

    private final WorkTripDetailService workTripDetailService;

    @Autowired
    public WorkTripDetailController(WorkTripDetailService workTripDetailService) {
        this.workTripDetailService = workTripDetailService;
    }

    // 출장/외근 등록
    @PostMapping("/create")
    public ResponseEntity<?> createWorkTrip(
            @RequestHeader("X-User-CompanyId")UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody WorkTripCreateReqDto reqDto){
        WorkTripResDto resDto = workTripDetailService.createWorkTrip(companyId, memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "출장/외근이 등록되었습니다."),
                HttpStatus.CREATED
        );
    }

    // 내 출장/외근 이력 조회
    @GetMapping
    public ResponseEntity<?> findWorkTrips(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId) {
        List<WorkTripResDto> resDtos = workTripDetailService.findWorkTrips(companyId, memberId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtos, "출장/외근 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    // 특정일 출장/외근 내역 조회
    @GetMapping("/daily/{dailyAttendanceId}")
    public ResponseEntity<?> findWorkTripsByDaily(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID dailyAttendanceId) {
        List<WorkTripResDto> resDtoList = workTripDetailService
                .findWorkTripsByDaily(companyId, memberId, dailyAttendanceId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "특정일 출장/외근 조회 성공"),
                HttpStatus.OK
        );
    }

    // 출장/외근 수정
    @PutMapping("/{workTripDetailId}")
    public ResponseEntity<?> updateWorkTrip(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID workTripDetailId,
            @Valid @RequestBody WorkTripUpdateReqDto reqDto) {
        WorkTripResDto resDto = workTripDetailService.updateWorkTrip(companyId, memberId, workTripDetailId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "출장/외근이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    // 출장/외근 삭제 (소프트 삭제)
    @DeleteMapping("/{workTripDetailId}")
    public ResponseEntity<?> deleteWorkTrip(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID workTripDetailId) {
        workTripDetailService.deleteWorkTrip(companyId, memberId, workTripDetailId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "출장/외근이 삭제되었습니다."),
                HttpStatus.OK
        );
    }
}
