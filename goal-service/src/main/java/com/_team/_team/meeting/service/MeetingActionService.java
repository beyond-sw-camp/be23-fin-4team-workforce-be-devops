package com._team._team.meeting.service;

import com._team._team.dto.BusinessException;
import com._team._team.meeting.domain.MeetingAction;
import com._team._team.meeting.domain.MeetingRecord;
import com._team._team.meeting.dto.reqdto.MeetingActionCreateReqDto;
import com._team._team.meeting.dto.reqdto.MeetingActionRateReqDto;
import com._team._team.meeting.dto.resdto.MeetingActionResDto;
import com._team._team.meeting.repository.MeetingActionRepository;
import com._team._team.meeting.repository.MeetingRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MeetingActionService {

    final MeetingActionRepository meetingActionRepository;
    final MeetingRecordRepository meetingRecordRepository;

    @Autowired
    public MeetingActionService(MeetingActionRepository meetingActionRepository,
                                MeetingRecordRepository meetingRecordRepository) {
        this.meetingActionRepository = meetingActionRepository;
        this.meetingRecordRepository = meetingRecordRepository;
    }

    @Transactional
    public MeetingActionResDto createAction(UUID meetingRecordId, UUID requesterId, MeetingActionCreateReqDto dto) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 매니저 확인
        if (!meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 액션 아이템을 생성할 수 있습니다.");
        }

        // [M2] assigneeId 는 필수 — DTO 레벨의 @NotNull 을 보조하는 서비스 레벨 가드.
        if (dto.getAssigneeId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "담당자(assigneeId)는 필수입니다.");
        }
        // [M2] assignee 는 해당 미팅의 당사자(멤버/매니저) 중 한 명이어야 합리적.
        if (!dto.getAssigneeId().equals(meetingRecord.getMemberId())
                && !dto.getAssigneeId().equals(meetingRecord.getManagerId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "액션 담당자는 해당 면담의 멤버 또는 매니저여야 합니다.");
        }

        MeetingAction meetingAction = MeetingAction.builder()
                .meetingRecord(meetingRecord)
                .assigneeId(dto.getAssigneeId())
                .description(dto.getDescription())
                .dueDate(dto.getDueDate())
                .isCompleted(false)
                .build();

        MeetingAction saved = meetingActionRepository.save(meetingAction);
        return MeetingActionResDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MeetingActionResDto> listActions(UUID meetingRecordId, UUID requesterId) {
        MeetingRecord meetingRecord = meetingRecordRepository.findById(meetingRecordId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "면담 기록을 찾을 수 없습니다. id=" + meetingRecordId));

        // 당사자 확인
        if (!meetingRecord.getMemberId().equals(requesterId) && !meetingRecord.getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 면담의 액션 아이템을 조회할 권한이 없습니다.");
        }

        return meetingActionRepository.findByMeetingRecord_MeetingRecordIdOrderByCreatedAtAsc(meetingRecordId)
                .stream()
                .map(MeetingActionResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingActionResDto> listPendingByAssignee(UUID assigneeId) {
        return meetingActionRepository.findByAssigneeIdAndIsCompletedFalse(assigneeId)
                .stream()
                .map(MeetingActionResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public MeetingActionResDto completeAction(UUID meetingRecordId, UUID meetingActionId, UUID requesterId) {
        MeetingAction meetingAction = meetingActionRepository.findById(meetingActionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "액션 아이템을 찾을 수 없습니다. id=" + meetingActionId));

        // 관계 검증
        if (!meetingAction.getMeetingRecord().getMeetingRecordId().equals(meetingRecordId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다. 액션 아이템이 해당 면담에 속하지 않습니다.");
        }

        // 담당자 확인
        if (!meetingAction.getAssigneeId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인에게 배정된 액션 아이템만 완료할 수 있습니다.");
        }

        // 상태 확인 (이미 완료된 경우 방지)
        if (meetingAction.isCompleted()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 완료된 액션 아이템입니다.");
        }

        meetingAction.complete();
        return MeetingActionResDto.from(meetingAction);
    }

    @Transactional
    public MeetingActionResDto rateAction(UUID meetingRecordId, UUID meetingActionId, UUID requesterId, MeetingActionRateReqDto dto) {
        MeetingAction meetingAction = meetingActionRepository.findById(meetingActionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "액션 아이템을 찾을 수 없습니다. id=" + meetingActionId));

        // 관계 검증
        if (!meetingAction.getMeetingRecord().getMeetingRecordId().equals(meetingRecordId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        // 매니저 확인
        if (!meetingAction.getMeetingRecord().getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 액션 아이템을 평가할 수 있습니다.");
        }

        // [M4] 완료되지 않은 액션은 평가 불가 — 엔티티 가드를 BusinessException 으로 변환
        try {
            meetingAction.rate(dto.getTlRating());
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return MeetingActionResDto.from(meetingAction);
    }

    @Transactional
    public MeetingActionResDto linkApproval(UUID meetingRecordId, UUID meetingActionId, UUID requesterId, UUID approvalId) {
        MeetingAction meetingAction = meetingActionRepository.findById(meetingActionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "액션 아이템을 찾을 수 없습니다. id=" + meetingActionId));

        // 관계 검증
        if (!meetingAction.getMeetingRecord().getMeetingRecordId().equals(meetingRecordId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        // 당사자 확인
        if (!meetingAction.getAssigneeId().equals(requesterId) && 
            !meetingAction.getMeetingRecord().getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }

        meetingAction.linkApproval(approvalId);
        return MeetingActionResDto.from(meetingAction);
    }

    @Transactional
    public void deleteAction(UUID meetingRecordId, UUID meetingActionId, UUID requesterId) {
        MeetingAction meetingAction = meetingActionRepository.findById(meetingActionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "액션 아이템을 찾을 수 없습니다. id=" + meetingActionId));

        // 관계 검증
        if (!meetingAction.getMeetingRecord().getMeetingRecordId().equals(meetingRecordId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        // 매니저 확인 (또는 생성자 확인)
        if (!meetingAction.getMeetingRecord().getManagerId().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "매니저만 액션 아이템을 삭제할 수 있습니다.");
        }

        meetingActionRepository.delete(meetingAction);
    }
}
