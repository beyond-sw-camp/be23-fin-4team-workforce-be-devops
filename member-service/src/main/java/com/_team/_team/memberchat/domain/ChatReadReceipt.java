package com._team._team.memberchat.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "mc_chat_read_receipt",
        uniqueConstraints = @UniqueConstraint(name = "uq_mc_read", columnNames = {"message_id", "member_id"}),
        indexes = {
                @Index(name = "idx_mc_read_member", columnList = "member_id,message_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatReadReceipt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(length = 100)
    private String deviceId;
}
