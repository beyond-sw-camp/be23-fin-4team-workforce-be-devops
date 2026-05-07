package com._team._team.attendance.service;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.dto.reqDto.LeaveOfAbsenceRequestReqDto;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 휴직 도메인 서비스
 */
@Service
@Transactional
public class MemberLeaveOfAbsenceService {

    private final MemberLeaveOfAbsenceRepository repository;

    @Autowired
    public MemberLeaveOfAbsenceService(MemberLeaveOfAbsenceRepository repository) {
        this.repository = repository;
    }

    /**
     * 직원 본인 휴직 신청
     */
    public MemberLeaveOfAbsence submit(UUID memberId, UUID companyId,
                                       LeaveOfAbsenceRequestReqDto reqDto) {
        // 날짜 유효성
        if (reqDto.getStartDate().isAfter(reqDto.getEndDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "시작일이 종료일보다 뒤일 수 없습니다.");
        }

        MemberLeaveOfAbsence memberLeaveOfAbsence = MemberLeaveOfAbsence.builder()
                .memberId(memberId)
                .companyId(companyId)
                .type(reqDto.getType())
                .startDate(reqDto.getStartDate())
                .endDate(reqDto.getEndDate())
                .isPaidYn(reqDto.getIsPaidYn())
                .reason(reqDto.getReason())
                .evidenceFileUrl(reqDto.getEvidenceFileUrl())
                .status(LeaveOfAbsenceApprovalStatus.REQUESTED)
                .requestedBy(memberId)
                .requestedAt(LocalDateTime.now())
                .build();

        return repository.save(memberLeaveOfAbsence);
    }

    /**
     * 결재 ID 연결, 프론트가 approval-service 결재 생성 후 호출
     */
    public MemberLeaveOfAbsence linkApprovalRequest(UUID leaveOfAbsenceId,
                                                    UUID memberId,
                                                    UUID approvalRequestId) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = findOwnRequest(leaveOfAbsenceId, memberId);
        memberLeaveOfAbsence.linkApprovalRequest(approvalRequestId);
        return memberLeaveOfAbsence;
    }

    /**
     * 본인 철회
     */
    public void cancel(UUID leaveOfAbsenceId, UUID memberId) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = findOwnRequest(leaveOfAbsenceId, memberId);
        memberLeaveOfAbsence.cancel();
    }

    /**
     * 결재 승인 반영, Kafka Consumer 가 호출
     */
    public void applyApproval(UUID approvalRequestId, UUID approverId,
                              LocalDateTime decidedAt) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = repository
                .findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "해당 결재에 연결된 휴직 신청을 찾을 수 없습니다."));
        memberLeaveOfAbsence.approve(approverId, decidedAt);
    }

    /**
     * 결재 반려 반영, Kafka Consumer 가 호출
     */
    public void applyRejection(UUID approvalRequestId, UUID approverId,
                               LocalDateTime decidedAt, String note) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = repository
                .findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "해당 결재에 연결된 휴직 신청을 찾을 수 없습니다."));
        memberLeaveOfAbsence.reject(approverId, decidedAt, note);
    }


    /**
     * 관리자 조기 복직 처리
     */
    public void endEarly(UUID companyId, UUID leaveOfAbsenceId, LocalDate actualEndDate) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = repository
                .findByLeaveOfAbsenceIdAndCompanyIdAndDelYn(
                        leaveOfAbsenceId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "휴직 기록을 찾을 수 없습니다."));

        if (actualEndDate.isBefore(memberLeaveOfAbsence.getStartDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "실제 종료일이 시작일보다 빠를 수 없습니다.");
        }

        memberLeaveOfAbsence.end(actualEndDate);
    }

    /**
     * 자연 종료 배치용, endDate 지난 ACTIVE 건 일괄 ENDED 처리
     */
    public int endExpired(LocalDate today) {
        List<MemberLeaveOfAbsence> expired = repository.findToEndByDate(today);
        expired.forEach(loa -> loa.end(loa.getEndDate()));
        return expired.size();
    }

    /**
     * 특정 날짜 휴직 중인지, 근태/급여/연차 배치에서 사용
     */
    @Transactional(readOnly = true)
    public boolean isOnLeaveOfAbsence(UUID memberId, LocalDate date) {
        return repository.findActiveOnDate(memberId, date).isPresent();
    }

    /**
     * 특정 날짜 휴직 정보 조회
     */
    @Transactional(readOnly = true)
    public java.util.Optional<MemberLeaveOfAbsence> findActiveOnDate(UUID memberId,
                                                                     LocalDate date) {
        return repository.findActiveOnDate(memberId, date);
    }

    // 본인 이력
    @Transactional(readOnly = true)
    public List<MemberLeaveOfAbsence> findMyHistory(UUID memberId) {
        return repository.findAllByMemberIdAndDelYnOrderByStartDateDesc(memberId, "N");
    }

    // 단건 조회
    @Transactional(readOnly = true)
    public MemberLeaveOfAbsence findById(UUID leaveOfAbsenceId, UUID companyId) {
        return repository.findByLeaveOfAbsenceIdAndCompanyIdAndDelYn(
                        leaveOfAbsenceId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "휴직 기록을 찾을 수 없습니다."));
    }

    // 관리자 상태별 목록
    @Transactional(readOnly = true)
    public List<MemberLeaveOfAbsence> findByCompanyAndStatus(UUID companyId,
                                                             LeaveOfAbsenceApprovalStatus status) {
        return repository.findAllByCompanyIdAndStatusAndDelYnOrderByStartDateAsc(
                companyId, status, "N");
    }

    // 본인 소유 검증
    private MemberLeaveOfAbsence findOwnRequest(UUID leaveOfAbsenceId, UUID memberId) {
        MemberLeaveOfAbsence memberLeaveOfAbsence = repository
                .findByLeaveOfAbsenceIdAndMemberId(leaveOfAbsenceId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "휴직 신청을 찾을 수 없습니다."));
        if (!"N".equals(memberLeaveOfAbsence.getDelYn())) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "이미 삭제된 휴직 신청입니다.");
        }
        return memberLeaveOfAbsence;
    }
}