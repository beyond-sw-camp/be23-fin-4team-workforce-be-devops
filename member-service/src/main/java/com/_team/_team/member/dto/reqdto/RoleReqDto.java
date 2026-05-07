package com._team._team.member.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleReqDto {

    @NotBlank(message = "역할명은 필수입니다.")
    private String name;

    private String description;

    private List<RolePermissionReqDto> permissions;
}
