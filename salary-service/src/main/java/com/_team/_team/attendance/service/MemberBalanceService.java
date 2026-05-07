package com._team._team.attendance.service;

import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.dto.reqDto.MemberBalanceCreateReqDto;
import com._team._team.attendance.dto.resDto.MemberBalanceResDto;
import com._team._team.attendance.dto.reqDto.MemberBalanceUseReqDto;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MemberBalanceService {

    private final MemberBalanceRepository memberBalanceRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;

    @Autowired
    public MemberBalanceService(MemberBalanceRepository memberBalanceRepository, DailyAttendanceRepository dailyAttendanceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
    }

    /**
     * 휴가 부여 (관리자용)
     * → POST /member-balance/grant 호출
     */
    public MemberBalanceResDto grantBalance(UUID companyId, MemberBalanceCreateReqDto reqDto){
        MemberBalance balance = reqDto.toEntity(companyId);
        MemberBalance savedBalance = memberBalanceRepository.save(balance);
        return MemberBalanceResDto.fromEntity(savedBalance);
    }

    // 휴가 조회 (사원용)
    @Transactional(readOnly = true)
    public List<MemberBalanceResDto> findBalances(UUID companyId, UUID memberId){
        List<MemberBalance> balances = memberBalanceRepository
                .findByMemberIdAndCompanyIdAndDelYn(memberId, companyId,"N");
        return balances.stream()
                .map(MemberBalanceResDto::fromEntity)
                .toList();
    }

    /**
     * 휴가 차감 (approval-service 연동)
     *
     * 사원이 연차 1일 신청 → approval-service에서 승인
     * → Kafka 이벤트: "memberId=abc, ANNUAL, 1.0일 차감"
     * → 이 메서드 호출 (내부 시스템 API)
     * ===== 워크플로우 =====
     * 1. findWithLock — 비관적 락으로 해당 사원의 잔여 조회
     * 2. 잔여 일수 검증
     * remaining < days → "잔여 연차가 부족합니다" 400 에러
     */
    public MemberBalanceResDto useBalance(UUID companyId, UUID memberId, MemberBalanceUseReqDto reqDto){

        // 비관적 락으로 잔여 조회
        MemberBalance memberBalance = memberBalanceRepository
                .findWithLock(companyId, memberId, reqDto.getBalanceType())
                .orElseThrow(()-> new BusinessException(
                        HttpStatus.NOT_FOUND, "사용 가능한 휴가 잔여가 없습니다."));

        // 잔여 일수 검증
        if(memberBalance.getRemaining() < reqDto.getDays()){
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "잔여 연차가 부족합니다. (잔여: " + memberBalance.getRemaining() + "일, 요청 : " + reqDto.getDays() + "일)");
        }

        // 휴가 차감
        memberBalance.use(reqDto.getDays());

        //연차 - 근태 연동 (DailyAttendance.status 변경)
        syncLeaveStatus(companyId, memberId, reqDto);

        return MemberBalanceResDto.fromEntity(memberBalance);
    }

    /**
     * 휴가 복구 (반려/취소 시)
     *
     * 승인된 연차 1일이 취소됨 (사원 취소 or 관리자 반려)
     * Kafka 이벤트: "memberId = abc, ANNUAL, 1.0일 복구"
     *
     * ===== 차감과 동일하게 비관적 락 사용 =====
     * 복구도 remaining을 변경하는 작업이므로 동시성 보호 필요
     * 차감과 복구가 동시에 일어나도 순차 처리
     */
    public MemberBalanceResDto restoreBalance(UUID companyId, UUID memberId, MemberBalanceUseReqDto reqDto) {

        // 비관적 락으로 잔여 조회
        MemberBalance memberBalance = memberBalanceRepository
                .findWithLock(companyId, memberId, reqDto.getBalanceType())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "복구 대상 휴가 잔여를 찾을 수 없습니다."));

        // 복구
        memberBalance.restore(reqDto.getDays());

        // 연차 취소 -> (DailyAttendance.status 원복)
        restoreLeaveStatus(companyId, memberId, reqDto);

        return MemberBalanceResDto.fromEntity(memberBalance);
    }

    /**
     * 직원 이월 동의 회신 - 회사 정책 isCarryoverConsentYn='Y' 일 때 본인 잔고에 대해 호출
     */
    public MemberBalanceResDto agreeCarryover(UUID companyId, UUID memberId, UUID memberBalanceId) {
        MemberBalance balance = memberBalanceRepository.findById(memberBalanceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "휴가 잔여를 찾을 수 없습니다."));
        if (!balance.getCompanyId().equals(companyId) || !balance.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 잔여만 동의 가능합니다.");
        }
        balance.agreeCarryover();
        return MemberBalanceResDto.fromEntity(balance);
    }

    /** 이월 동의 철회 */
    public MemberBalanceResDto revokeCarryoverConsent(UUID companyId, UUID memberId, UUID memberBalanceId) {
        MemberBalance balance = memberBalanceRepository.findById(memberBalanceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "휴가 잔여를 찾을 수 없습니다."));
        if (!balance.getCompanyId().equals(companyId) || !balance.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 잔여만 철회 가능합니다.");
        }
        balance.revokeCarryoverConsent();
        return MemberBalanceResDto.fromEntity(balance);
    }

    // ===================== 내부 메서드 =====================
    /**
     * 연차-근태 연동: 휴가 승인 시 DailyAttendance.status 변경
     * - DailyAttendance가 없으면 → 새로 생성 (해당 날짜가 아직 안 왔을 수 있음)
     * - days >= 1.0 → status = LEAVE (전일 휴가)
     * - days < 1.0 (0.5) → status = HALF (반차)
     *
     * - MemberBalance 차감과 DailyAttendance 상태 변경이 같은 트랜잭션
     * - 하나라도 실패하면 전체 롤백 → 정합성 보장
     */
    private void syncLeaveStatus(UUID companyId, UUID memberId, MemberBalanceUseReqDto reqDto) {

        // leaveDate가 null이면 연동 스킵 (하위 호환)
        if (reqDto.getLeaveDate() == null) {
            return;
        }

        // DailyAttendance 조회 - 없으면 새로 생성
        DailyAttendance daily = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, reqDto.getLeaveDate())
                .orElseGet(() -> {
                    // 아직 해당 날짜의 DailyAttendance가 없음 (미래 날짜 등)
                    DailyAttendance newDaily = DailyAttendance.builder()
                            .companyId(companyId)
                            .memberId(memberId)
                            .attendanceDate(reqDto.getLeaveDate())
                            .status(AttendanceStatus.ABSENT)  // 초기값 ABSENT
                            .build();
                    return dailyAttendanceRepository.save(newDaily);
                });

        // 전일 휴가(1일 이상) vs 반차(0.5일)
        if (reqDto.getDays() >= 1.0) {
            daily.updateStatus(AttendanceStatus.LEAVE);
        } else {
            daily.updateStatus(AttendanceStatus.HALF);
        }
    }

    /**
     * 연차-근태 연동: 휴가 취소/반려 시 DailyAttendance.status 원복
     * - DailyAttendance가 있으면 → status = ABSENT로 원복
     * - DailyAttendance가 없으면 → 이미 삭제된 상태이므로 무시
     */
    private void restoreLeaveStatus(UUID companyId, UUID memberId, MemberBalanceUseReqDto reqDto) {

        // leaveDate가 null이면 연동 스킵
        if (reqDto.getLeaveDate() == null) {
            return;
        }

        dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, reqDto.getLeaveDate())
                .ifPresent(daily -> {
                    daily.updateStatus(AttendanceStatus.ABSENT);
                });
    }
}
