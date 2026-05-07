package com._team._team.memberchat.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.memberchat.dto.req.ChatSendRequest;
import com._team._team.memberchat.dto.res.ChatMessageEvent;
import com._team._team.memberchat.dto.res.ChatMessageResponse;
import com._team._team.memberchat.service.ChatMessageService;
import com._team._team.memberchat.service.FileService;
import com._team._team.memberchat.service.ReadReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 채팅 메시지 조회·전송·동기화·읽음
@RestController
@RequestMapping("/member-chat")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ReadReceiptService readReceiptService;
    private final FileService fileService;

    // 첨부 멀티파트 업로드( /files/upload 와 동일 로직 — 경로 단순화·매칭 안정화 )
    @PostMapping("/file-upload")
    public ResponseEntity<?> uploadAttachment(@RequestHeader("X-User-UUID") UUID uploader,
                                              @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("빈 파일입니다."));
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "upload.bin";
        }
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(ApiResponse.success(
                    fileService.uploadViaServer(uploader, name, file.getContentType(), in, file.getSize()), "ok"));
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패", e);
        }
    }

    // 채팅방 메시지 목록
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> history(@RequestHeader("X-User-UUID") UUID memberId,
                                     @PathVariable Long roomId,
                                     @RequestParam(required = false) Long cursor,
                                     @RequestParam(defaultValue = "50") int size) {
        List<ChatMessageResponse> messages = chatMessageService.history(memberId, roomId, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(messages, "ok"));
    }

    // 메시지 전송(웹 폴백)
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> send(@RequestHeader("X-User-UUID") UUID memberId,
                                  @PathVariable Long roomId,
                                  @RequestBody @Valid ChatSendRequest req) {
        ChatMessageEvent event = chatMessageService.saveAndPublish(memberId, roomId, req);
        return ResponseEntity.ok(ApiResponse.success(event, "sent"));
    }

    // 미수신 메시지 동기화
    @GetMapping("/sync")
    public ResponseEntity<?> sync(@RequestHeader("X-User-UUID") UUID memberId,
                                  @RequestParam Long roomId,
                                  @RequestParam(required = false) Long lastSeenMessageId,
                                  @RequestParam(defaultValue = "200") int size) {
        List<ChatMessageResponse> messages = chatMessageService.sync(memberId, roomId, lastSeenMessageId, size);
        return ResponseEntity.ok(ApiResponse.success(messages, "ok"));
    }

    // 메시지 수정
    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<?> edit(@RequestHeader("X-User-UUID") UUID memberId,
                                  @PathVariable Long messageId,
                                  @RequestBody Map<String, String> body) {
        ChatMessageEvent updated = chatMessageService.editMessage(memberId, messageId, body.getOrDefault("content", ""));
        return ResponseEntity.ok(ApiResponse.success(updated, "updated"));
    }

    // 메시지 삭제
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> delete(@RequestHeader("X-User-UUID") UUID memberId,
                                    @RequestHeader(value = "X-User-Role", required = false) String role,
                                    @PathVariable Long messageId) {
        boolean admin = "HR_ADMIN".equals(role) || "AUDITOR".equals(role);
        ChatMessageEvent event = chatMessageService.deleteMessage(memberId, messageId, admin);
        return ResponseEntity.ok(ApiResponse.success(event, "deleted"));
    }

    // 읽음 처리 (특정 메시지까지)
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<?> read(@RequestHeader("X-User-UUID") UUID memberId,
                                  @PathVariable Long roomId,
                                  @RequestParam Long messageId,
                                  @RequestParam(required = false, defaultValue = "") String deviceId) {
        readReceiptService.ack(memberId, roomId, messageId, deviceId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true), "ack"));
    }

    // 방 진입 시: 방의 최신 메시지까지 일괄 읽음 처리 (카톡식 자동 ack)
    @PostMapping("/rooms/{roomId}/read-latest")
    public ResponseEntity<?> readLatest(@RequestHeader("X-User-UUID") UUID memberId,
                                        @PathVariable Long roomId,
                                        @RequestParam(required = false, defaultValue = "") String deviceId) {
        Long lastReadMessageId = readReceiptService.ackLatest(memberId, roomId, deviceId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("ok", true, "lastReadMessageId", lastReadMessageId == null ? 0L : lastReadMessageId),
                "ack"));
    }
}
