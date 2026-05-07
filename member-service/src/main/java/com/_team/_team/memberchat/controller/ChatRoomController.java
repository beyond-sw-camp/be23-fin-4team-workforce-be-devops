package com._team._team.memberchat.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.memberchat.dto.req.CreateDirectRoomRequest;
import com._team._team.memberchat.dto.req.CreateGroupRoomRequest;
import com._team._team.memberchat.dto.res.ChatParticipantResponse;
import com._team._team.memberchat.dto.res.ChatRoomResponse;
import com._team._team.memberchat.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 채팅방 생성·목록·멤버·삭제
@RestController
@RequestMapping("/member-chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    // 1:1 방 조회·생성
    @PostMapping("/direct")
    public ResponseEntity<?> createDirect(@RequestHeader("X-User-UUID") UUID me,
                                          @RequestHeader("X-User-CompanyId") UUID companyId,
                                          @RequestBody @Valid CreateDirectRoomRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.getOrCreateDirectRoom(me, companyId, req), "ok"));
    }

    // 그룹 방 생성
    @PostMapping("/group")
    public ResponseEntity<?> createGroup(@RequestHeader("X-User-UUID") UUID me,
                                         @RequestHeader("X-User-CompanyId") UUID companyId,
                                         @RequestBody @Valid CreateGroupRoomRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.createGroup(me, companyId, req), "created"));
    }

    // 내 채팅방 목록
    @GetMapping
    public ResponseEntity<?> myRooms(@RequestHeader("X-User-UUID") UUID me) {
        List<ChatRoomResponse> rooms = chatRoomService.myRooms(me);
        return ResponseEntity.ok(ApiResponse.success(rooms, "ok"));
    }

    // 방 참여자 목록 (이름·직급·소속·역할) — 방 참여자만 조회 가능
    @GetMapping("/{roomId}/participants")
    public ResponseEntity<?> listParticipants(@RequestHeader("X-User-UUID") UUID me,
                                              @PathVariable Long roomId) {
        List<ChatParticipantResponse> participants = chatRoomService.listParticipants(me, roomId);
        return ResponseEntity.ok(ApiResponse.success(participants, "ok"));
    }

    // 멤버 초대
    @PostMapping("/{roomId}/members/{memberId}")
    public ResponseEntity<?> addMember(@RequestHeader("X-User-UUID") UUID me,
                                       @PathVariable Long roomId,
                                       @PathVariable UUID memberId) {
        chatRoomService.addMember(me, roomId, memberId);
        return ResponseEntity.ok(ApiResponse.success(null, "added"));
    }

    // 방 나가기
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leave(@RequestHeader("X-User-UUID") UUID me,
                                   @PathVariable Long roomId) {
        chatRoomService.leave(me, roomId);
        return ResponseEntity.ok(ApiResponse.success(null, "left"));
    }

    // 방 삭제
    @DeleteMapping("/{roomId}")
    public ResponseEntity<?> delete(@RequestHeader("X-User-UUID") UUID me,
                                    @RequestHeader(value = "X-User-Role", required = false) String role,
                                    @PathVariable Long roomId) {
        boolean adminOverride = "HR_ADMIN".equals(role);
        chatRoomService.deleteRoom(me, roomId, adminOverride);
        return ResponseEntity.ok(ApiResponse.success(null, "deleted"));
    }
}
