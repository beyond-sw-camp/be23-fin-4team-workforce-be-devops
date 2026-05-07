package com._team._team.contract.domain.enums;

public enum ContractStatus {
    CREATED,    // 생성됨 (발송 전)
    SENT,       // 발송됨
    SIGNED,     // 체결완료
    REJECTED,   // 거절됨
    CANCELED,   // 회수됨
}
