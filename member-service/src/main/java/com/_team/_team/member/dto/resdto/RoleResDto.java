package com._team._team.member.dto.resdto;

import com._team._team.member.domain.Role;
import com._team._team.member.domain.RolePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResDto {

    private UUID roleId;
    private String name;
    private String description;
    private int displayOrder;
    private List<RolePermissionResDto> permissions;

    public static RoleResDto fromEntity(Role role) {
        return RoleResDto.builder()
                .roleId(role.getRoleId())
                .name(role.getName())
                .description(role.getDescription())
                .displayOrder(role.getDisplayOrder())
                .permissions(role.getRolePermissionList().stream()
                        .map(RolePermissionResDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
}