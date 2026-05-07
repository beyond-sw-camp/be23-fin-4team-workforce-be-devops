package com._team._team.memberchat.error;

import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {
    private final ChatErrorCode errorCode;

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ChatException(ChatErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
