package com._team._team.member.dto.resdto;


import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import com._team._team.member.constant.PermissionRange;
import com._team._team.member.domain.RolePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionResDto {

    private UUID rolePermissionId;
    private Resource resource;
    private Action action;
    private PermissionRange permissionRange;

    public static RolePermissionResDto fromEntity(RolePermission rp) {
        return RolePermissionResDto.builder()
                .rolePermissionId(rp.getRolePermissionId())
                .resource(rp.getPermission().getResource())
                .action(rp.getPermission().getAction())
                .permissionRange(rp.getPermissionRange())
                .build();
    }
}