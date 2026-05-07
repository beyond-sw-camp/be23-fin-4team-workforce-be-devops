package com._team._team.memberchat.config;

import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * REST 와 STOMP 양쪽에서 ChatException 을 표준 포맷으로 응답.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com._team._team.memberchat.controller")
public class ChatExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<?> handleRest(ChatException e) {
        ChatErrorCode c = e.getErrorCode();
        return ResponseEntity.status(c.getStatus()).body(Map.of(
                "success", false,
                "code", c.getCode(),
                "message", e.getMessage(),
                "data", null
        ));
    }

    @MessageExceptionHandler(ChatException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleStomp(ChatException e) {
        ChatErrorCode c = e.getErrorCode();
        log.warn("STOMP error: {} - {}", c.getCode(), e.getMessage());
        return Map.of("code", c.getCode(), "message", e.getMessage());
    }
}
