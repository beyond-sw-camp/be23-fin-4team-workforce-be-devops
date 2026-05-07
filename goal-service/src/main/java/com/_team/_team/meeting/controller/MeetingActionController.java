package com._team._team.meeting.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.meeting.dto.reqdto.LinkApprovalReqDto;
import com._team._team.meeting.dto.reqdto.MeetingActionCreateReqDto;
import com._team._team.meeting.dto.reqdto.MeetingActionRateReqDto;
import com._team._team.meeting.dto.resdto.MeetingActionResDto;
import com._team._team.meeting.service.MeetingActionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 면담 액션 아이템 생성, 조회, 완료, 평가, 결재 연결, 삭제 컨트롤러
@RestController
@RequestMapping("/meeting")
public class MeetingActionController {

    final MeetingActionService meetingActionService;

    @Autowired
    public MeetingActionController(MeetingActionService meetingActionService) {
        this.meetingActionService = meetingActionService;
    }

    // 액션 아이템 생성
    @CheckPermission(resource = Resource.MEETING, action = Action.CREATE)
    @PostMapping("/{meetingRecordId}/action")
    public ApiResponse<MeetingActionResDto> createAction(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId,
            @RequestBody @Valid MeetingActionCreateReqDto dto) {
        return ApiResponse.success(meetingActionService.createAction(meetingRecordId, UUID.fromString(requesterId), dto), "액션 아이템이 생성되었습니다.");
    }

    // 액션 아이템 목록 조회
    @CheckPermission(resource = Resource.MEETING, action = Action.READ)
    @GetMapping("/{meetingRecordId}/action")
    public ApiResponse<List<MeetingActionResDto>> listActions(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId) {
        return ApiResponse.success(meetingActionService.listActions(meetingRecordId, UUID.fromString(requesterId)), "액션 아이템 목록 조회 성공");
    }

    // 담당자별 미완료 액션 아이템 조회 (나의 것)
    @CheckPermission(resource = Resource.MEETING, action = Action.READ)
    @GetMapping("/action/me/pending")
    public ApiResponse<List<MeetingActionResDto>> listMyPendingActions(
            @RequestHeader("X-User-UUID") String memberId) {
        return ApiResponse.success(meetingActionService.listPendingByAssignee(UUID.fromString(memberId)), "미완료 액션 아이템 목록 조회 성공");
    }

    // 액션 아이템 완료 처리
    @CheckPermission(resource = Resource.MEETING, action = Action.UPDATE)
    @PatchMapping("/{meetingRecordId}/action/{meetingActionId}/complete")
    public ApiResponse<MeetingActionResDto> completeAction(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId,
            @PathVariable UUID meetingActionId) {
        return ApiResponse.success(meetingActionService.completeAction(meetingRecordId, meetingActionId, UUID.fromString(requesterId)), "액션 아이템이 완료되었습니다.");
    }

    // 액션 아이템 평가
    @CheckPermission(resource = Resource.MEETING, action = Action.UPDATE)
    @PatchMapping("/{meetingRecordId}/action/{meetingActionId}/rate")
    public ApiResponse<MeetingActionResDto> rateAction(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId,
            @PathVariable UUID meetingActionId,
            @RequestBody @Valid MeetingActionRateReqDto dto) {
        return ApiResponse.success(meetingActionService.rateAction(meetingRecordId, meetingActionId, UUID.fromString(requesterId), dto), "액션 아이템 평가가 완료되었습니다.");
    }

    // 액션 아이템 결재 연결
    @CheckPermission(resource = Resource.MEETING, action = Action.UPDATE)
    @PatchMapping("/{meetingRecordId}/action/{meetingActionId}/approval")
    public ApiResponse<MeetingActionResDto> linkApproval(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId,
            @PathVariable UUID meetingActionId,
            @RequestBody @Valid LinkApprovalReqDto dto) {
        return ApiResponse.success(meetingActionService.linkApproval(meetingRecordId, meetingActionId, UUID.fromString(requesterId), dto.getApprovalId()), "결재가 연결되었습니다.");
    }

    // 액션 아이템 삭제
    @CheckPermission(resource = Resource.MEETING, action = Action.DELETE)
    @DeleteMapping("/{meetingRecordId}/action/{meetingActionId}")
    public ApiResponse<Void> deleteAction(
            @RequestHeader("X-User-UUID") String requesterId,
            @PathVariable UUID meetingRecordId,
            @PathVariable UUID meetingActionId) {
        meetingActionService.deleteAction(meetingRecordId, meetingActionId, UUID.fromString(requesterId));
        return ApiResponse.success(null, "액션 아이템이 삭제되었습니다.");
    }
}
