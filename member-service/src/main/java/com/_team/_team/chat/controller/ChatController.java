package com._team._team.chat.controller;

import com._team._team.chat.dto.reqdto.*;
import com._team._team.chat.service.ChatService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 챗봇 질문
    @PostMapping
    public ResponseEntity<?> chat(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChatReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        chatService.chat(memberId, companyId,
                                memberPositionId, authorization, reqDto),
                        "챗봇 응답 성공"),
                HttpStatus.OK
        );
    }

    // 대화 이력 조회
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        chatService.getChatHistory(memberId, companyId),
                        "대화 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    // 대화 이력 삭제
    @DeleteMapping("/history")
    public ResponseEntity<?> deleteChatHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        chatService.deleteChatHistory(memberId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "대화 이력 삭제 성공"),
                HttpStatus.OK
        );
    }
}