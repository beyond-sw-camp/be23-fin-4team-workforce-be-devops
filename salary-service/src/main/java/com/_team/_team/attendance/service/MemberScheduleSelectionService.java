package com._team._team.attendance.service;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.enums.ScheduleApprovalStatus;
import com._team._team.attendance.dto.reqDto.MemberScheduleSelectionReqDto;
import com._team._team.attendance.dto.resDto.MemberScheduleSelectionResDto;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * 시차출퇴근 스케줄 선택 서비스
 */
@Service
@Transactional
public class MemberScheduleSelectionService {

    private static final String ACTIVE = "Y";

    private final MemberScheduleSelectionRepository memberScheduleSelectionRepository;
    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;

    @Autowired
    public MemberScheduleSelectionService(
            MemberScheduleSelectionRepository memberScheduleSelectionRepository,
            FlexibleTimeSlotRepository flexibleTimeSlotRepository) {
        this.memberScheduleSelectionRepository = memberScheduleSelectionRepository;
        this.flexibleTimeSlotRepository = flexibleTimeSlotRepository;
    }
    /**
     * 슬롯 선택 제출
     * 최초 선택과 월 중 변경 요청 모두 이 메서드로 처리
     */
    public MemberScheduleSelectionResDto submit(UUID companyId,
                                                UUID memberId,
                                                MemberScheduleSelectionReqDto reqDto) {

        // 슬롯 유효성 검증
        FlexibleTimeSlot slot = flexibleTimeSlotRepository
                .findBySlotIdAndCompanyId(reqDto.getSlotId(), companyId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "스케줄 슬롯을 찾을 수 없습니다."));

        if (!ACTIVE.equals(slot.getActiveYn())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "폐지된 스케줄 슬롯은 선택할 수 없습니다.");
        }

        // 같은 달 PENDING 중복 차단
        boolean hasPending = memberScheduleSelectionRepository
                .existsByMemberIdAndTargetYearMonthAndApprovalStatus(
                        memberId, reqDto.getTargetYearMonth(),
                        ScheduleApprovalStatus.PENDING);
        if (hasPending) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "이미 결재 대기 중인 요청이 있습니다.");
        }

        // 월 중 변경 여부 판단 (기존 유효 선택 있으면 변경 요청)
        boolean isChangeRequest = memberScheduleSelectionRepository
                .findCurrentActive(memberId, reqDto.getTargetYearMonth())
                .isPresent();

        if (isChangeRequest &&
                (reqDto.getRequestReason() == null || reqDto.getRequestReason().isBlank())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "변경 사유를 입력해주세요.");
        }

        // PENDING 상태로 INSERT
        LocalDateTime now = LocalDateTime.now();
        MemberScheduleSelection selection = MemberScheduleSelection.builder()
                .memberId(memberId)
                .companyId(companyId)
                .targetYearMonth(reqDto.getTargetYearMonth())
                .slotId(reqDto.getSlotId())
                .breakStart(reqDto.getBreakStart())
                .breakEnd(reqDto.getBreakEnd())
                .approvalStatus(ScheduleApprovalStatus.PENDING)
                .requestReason(reqDto.getRequestReason())
                .requestedBy(memberId)
                .requestedAt(now)
                .build();

        MemberScheduleSelection saved = memberScheduleSelectionRepository.save(selection);
        return MemberScheduleSelectionResDto.fromEntity(saved);
    }

    /**
     * 본인이 결재 전 철회
     * PENDING 상태만 취소 가능
     */
    public void cancel(UUID selectionId, UUID memberId) {
        MemberScheduleSelection selection = findOwnSelection(selectionId, memberId);
        selection.cancel();
    }

    /**
     * 결재 승인 반영
     * ScheduleApprovalEventConsumer 가 Kafka 이벤트 수신 후 호출
     */
    public void applyApproval(UUID selectionId,
                              UUID approvalRequestId,
                              UUID approverId,
                              LocalDateTime decidedAt) {
        MemberScheduleSelection selection = memberScheduleSelectionRepository
                .findById(selectionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "스케줄 선택을 찾을 수 없습니다."));

        selection.linkApprovalRequest(approvalRequestId);
        selection.approve(approverId, decidedAt);
    }

    /**
     * 결재 반려·취소 반영
     */
    public void applyRejection(UUID selectionId,
                               UUID approvalRequestId,
                               UUID approverId,
                               LocalDateTime decidedAt,
                               String note) {
        MemberScheduleSelection selection = memberScheduleSelectionRepository
                .findById(selectionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "스케줄 선택을 찾을 수 없습니다."));

        selection.linkApprovalRequest(approvalRequestId);
        selection.reject(approverId, decidedAt, note);
    }

    /**
     * 시스템 자동 할당 (AUTO)
     * 신규 입사자 또는 슬롯 입력 마감일 경과 미선택자 대상으로 배치에서 호출
     */
    public MemberScheduleSelectionResDto autoAssignDefault(UUID companyId,
                                                           UUID memberId,
                                                           String targetYearMonth,
                                                           UUID slotId,
                                                           UUID systemActorId) {
        LocalDateTime now = LocalDateTime.now();

        MemberScheduleSelection selection = MemberScheduleSelection.builder()
                .memberId(memberId)
                .companyId(companyId)
                .targetYearMonth(targetYearMonth)
                .slotId(slotId)
                .approvalStatus(ScheduleApprovalStatus.AUTO)
                .requestReason("시스템 자동 할당")
                .requestedBy(systemActorId)
                .requestedAt(now)
                .build();

        MemberScheduleSelection saved = memberScheduleSelectionRepository.save(selection);
        return MemberScheduleSelectionResDto.fromEntity(saved);
    }

    /**
     * 내 현재 적용 슬롯 조회
     * APPROVED 또는 AUTO 중 최신 건
     */
    @Transactional(readOnly = true)
    public MemberScheduleSelectionResDto findMyCurrent(UUID memberId, String targetYearMonth) {
        return memberScheduleSelectionRepository
                .findCurrentActive(memberId, targetYearMonth)
                .map(MemberScheduleSelectionResDto::fromEntity)
                .orElse(null);
    }


    /**
     * 내 해당 월 이력 조회
     */
    @Transactional(readOnly = true)
    public List<MemberScheduleSelectionResDto> findMyHistory(UUID memberId,
                                                             String targetYearMonth) {
        return memberScheduleSelectionRepository
                .findAllByMemberIdAndTargetYearMonth(memberId, targetYearMonth)
                .stream()
                .map(MemberScheduleSelectionResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 본인 소유 검증
    private MemberScheduleSelection findOwnSelection(UUID selectionId, UUID memberId) {
        MemberScheduleSelection selection = memberScheduleSelectionRepository
                .findById(selectionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "선택 내역을 찾을 수 없습니다."));

        if (!selection.getMemberId().equals(memberId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "본인 요청만 취소할 수 있습니다.");
        }
        return selection;
    }
}
