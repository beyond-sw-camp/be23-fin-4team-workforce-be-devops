package com._team._team.member.domain;

import com._team._team.domain.BaseTimeEntity;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Permission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "permission_id")
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    private String description;
}
