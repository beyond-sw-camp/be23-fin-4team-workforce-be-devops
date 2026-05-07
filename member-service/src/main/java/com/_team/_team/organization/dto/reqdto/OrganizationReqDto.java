package com._team._team.organization.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationReqDto {

    @NotBlank(message = "조직명은 필수입니다.")
    private String name;

    private UUID parentId; // 상위 조직 (null이면 최상위)
}