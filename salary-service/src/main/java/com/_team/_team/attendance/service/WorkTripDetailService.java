package com._team._team.attendance.service;

import com._team._team.attendance.repository.WorkTripDetailRepository;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.dto.reqDto.WorkTripCreateReqDto;
import com._team._team.attendance.dto.reqDto.WorkTripUpdateReqDto;
import com._team._team.attendance.dto.resDto.WorkTripResDto;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


/**
 * 출장/외근 관리 서비스
 *
 * 사원의 출장(BUSINESS_TRIP), 외근(OUTSIDE_WORK) 등록/조회/수정/삭제
 * - 출장/외근은 해당 날짜의 DailyAttendance와 연결
 * - 경비(expenseAmount)가 있으면 approval-service에서 결재 후 expenseStatus 변경
 *
 * ===== 전체 흐름 =====
 * [사원] 출장 등록 → createWorkTrip()
 *   → 해당 날짜 DailyAttendance와 ManyToOne 연결
 *   → 경비 있으면 expenseStatus = PENDING (결재 대기)
 *
 * [관리자] 경비 승인 → approval-service에서 Kafka 이벤트
 *   → expenseStatus = APPROVED/REJECTED (별도 Consumer에서 처리)
 *
 * [사원] 내 출장 이력 조회 → findWorkTrips()
 * [사원] 특정일 출장 상세 → findWorkTripsByDaily()
 */
@Service
@Transactional
public class WorkTripDetailService {
    private final WorkTripDetailRepository workTripDetailRepository;
    private final DailyAttendanceService dailyAttendanceService;

    @Autowired
    public WorkTripDetailService(WorkTripDetailRepository workTripDetailRepository, DailyAttendanceService dailyAttendanceService) {
        this.workTripDetailRepository = workTripDetailRepository;
        this.dailyAttendanceService = dailyAttendanceService;
    }

    /**
     * 출장/외근 등록
     * [단계 1] findOrCreateDaily — 해당 날짜의 DailyAttendance 조회/생성
     *          출장일에 출근을 안 할 수도 있으니, 없으면 새로 생성
     *          → 출장도 근태 기록의 일부이므로 DailyAttendance가 반드시 필요
     *
     * [단계 2] toEntity — DTO → 엔티티 변환 (dailyAttendance 연결)
     *
     * [단계 3] save — DB 저장
     *          경비가 있으면 expenseStatus = PENDING (Builder.Default)
     */
    public WorkTripResDto createWorkTrip (UUID companyId, UUID memberId, WorkTripCreateReqDto reqDto){
        // 해당 날짜의 DailyAttendance 조회/생성
        DailyAttendance dailyAttendance = dailyAttendanceService
                .findOrCreateDaily(companyId, memberId, reqDto.getDate());

        // 엔티티 생성 + 저장
        WorkTripDetail detail = reqDto.toEntity(dailyAttendance, companyId, memberId);
        WorkTripDetail saved = workTripDetailRepository.save(detail);

        return WorkTripResDto.fromEntity(saved);
    }

    /**
     * 내 출장/외근 이력 조회
     */
    @Transactional(readOnly = true)
    public List<WorkTripResDto> findWorkTrips(UUID companyId, UUID memberId){
        List<WorkTripDetail> details = workTripDetailRepository
                .findByMemberIdAndCompanyIdAndDelYn(memberId, companyId, "N");

        return details.stream()
                .map(WorkTripResDto::fromEntity)
                .toList();
    }

    /**
     * 특정일 출장/외근 내역 조회
     * 하루에 여러 건이면 리스트로 반환
     */
    @Transactional(readOnly = true)
    public List<WorkTripResDto> findWorkTripsByDaily(UUID companyId, UUID memberId, UUID dailyAttendanceId){
        List<WorkTripDetail> details = workTripDetailRepository
                .findByMemberIdAndCompanyIdAndDelYnAndDailyAttendanceDailyAttendanceId(
                        memberId, companyId, "N", dailyAttendanceId);

        return details.stream()
                .map(WorkTripResDto::fromEntity)
                .toList();
    }

    /** 출장/외근 수정 */
    public WorkTripResDto updateWorkTrip (UUID companyId, UUID memberId, UUID workTripDetailId, WorkTripUpdateReqDto reqDto){
        WorkTripDetail detail = workTripDetailRepository
                .findByWorkTripDetailIdAndMemberIdAndCompanyIdAndDelYn(workTripDetailId, memberId, companyId, "N")
                .orElseThrow(()-> new BusinessException(
                        HttpStatus.NOT_FOUND, "출장/외근 내역을 찾을 수 없습니다."));
        detail.update(reqDto);
        return WorkTripResDto.fromEntity(detail);
    }

    /** 출장/외근 삭제*/
    public void deleteWorkTrip(UUID companyId, UUID memberId, UUID workTripDetailId){
        WorkTripDetail detail = workTripDetailRepository
                .findByWorkTripDetailIdAndMemberIdAndCompanyIdAndDelYn(workTripDetailId, memberId, companyId, "N")
                .orElseThrow(()-> new BusinessException(
                        HttpStatus.NOT_FOUND, "출장/외근 내역을 찾을 수 없습니다."));
        detail.delete();
    }
}
