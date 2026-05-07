package com._team._team.memberchat.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mc_chat_message_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessageImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 100)
    private String mimeType;

    private Long sizeBytes;

    /** PENDING_SCAN | CLEAN | INFECTED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String scanStatus = "PENDING_SCAN";
}
