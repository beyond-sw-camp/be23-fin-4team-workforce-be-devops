package com._team._team.memberchat.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.memberchat.domain.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mc_chat_message", indexes = {
        @Index(name = "idx_mc_msg_room_id", columnList = "chat_room_id,id"),
        @Index(name = "idx_mc_msg_room_created", columnList = "chat_room_id,createdAt")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_mc_msg_client_id", columnNames = {"client_message_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MessageType type = MessageType.NORMAL;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean edited = false;

    private LocalDateTime editedAt;

    @OneToMany(mappedBy = "chatMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessageImage> attachments = new ArrayList<>();

    public void updateContent(String newContent) {
        this.content = newContent;
        this.edited = true;
        this.editedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deleted = true;
    }

    public boolean isOlderThan(Duration window) {
        return getCreatedAt() != null && getCreatedAt().plus(window).isBefore(LocalDateTime.now());
    }
}
