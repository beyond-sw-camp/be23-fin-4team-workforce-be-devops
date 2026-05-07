package com._team._team.memberchat.domain.enums;

public enum MessageType {
    NORMAL,   // 일반 텍스트
    NOTICE,   // 공지 (권한자만)
    SYSTEM,   // 입/퇴장, 초대 등 서버 생성
    IMAGE,    // 이미지 첨부
    FILE,     // 일반 파일
    REPLY     // 답장(replyToId 필요)
}
