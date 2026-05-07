package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class OrganizationChangedEvent {
    private UUID organizationId;
    private boolean deleted; // ← 추가

    // 생성/수정용
    public OrganizationChangedEvent(UUID organizationId) {
        this.organizationId = organizationId;
        this.deleted = false;
    }

    // 삭제용
    public static OrganizationChangedEvent deleted(UUID organizationId) {
        return new OrganizationChangedEvent(organizationId, true);
    }
}