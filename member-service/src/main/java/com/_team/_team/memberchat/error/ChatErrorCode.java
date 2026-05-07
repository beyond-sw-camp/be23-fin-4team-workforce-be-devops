package com._team._team.memberchat.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * REST + STOMP 공통 에러 코드 표준.
 * 포맷: CHAT_{httpStatus}{seq}
 */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode {

    VALIDATION_ERROR("CHAT_4001", HttpStatus.BAD_REQUEST, "요청 파라미터가 유효하지 않습니다."),
    INVALID_JWT("CHAT_4011", HttpStatus.UNAUTHORIZED, "인증 토큰이 유효하지 않습니다."),
    NOT_PARTICIPANT("CHAT_4031", HttpStatus.FORBIDDEN, "방에 대한 접근 권한이 없습니다."),
    ROLE_FORBIDDEN("CHAT_4032", HttpStatus.FORBIDDEN, "역할 권한이 부족합니다."),
    ROOM_DELETED("CHAT_4033", HttpStatus.FORBIDDEN, "삭제되었거나 비활성화된 방입니다."),
    INACTIVE_USER("CHAT_4034", HttpStatus.FORBIDDEN, "비활성 사용자입니다."),
    NOTICE_FORBIDDEN("CHAT_4035", HttpStatus.FORBIDDEN, "공지 전송 권한이 없습니다."),
    EDIT_FORBIDDEN("CHAT_4036", HttpStatus.FORBIDDEN, "본인의 메시지만 수정할 수 있습니다."),
    EDIT_WINDOW_EXPIRED("CHAT_4037", HttpStatus.FORBIDDEN, "수정 가능한 기간이 지났습니다."),

    ROOM_NOT_FOUND("CHAT_4041", HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    MESSAGE_NOT_FOUND("CHAT_4042", HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    FILE_NOT_FOUND("CHAT_4043", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),

    DUPLICATE("CHAT_4091", HttpStatus.CONFLICT, "중복된 요청입니다(idempotency)."),
    FILE_QUARANTINED("CHAT_4092", HttpStatus.CONFLICT, "파일이 격리(검사중/차단) 상태입니다."),

    RATE_LIMIT("CHAT_4291", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    INTERNAL("CHAT_5001", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;
}
