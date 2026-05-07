package com._team._team.organization.dto.resdto;

import com._team._team.organization.domain.Organization;
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
public class OrganizationResDto {

    private UUID organizationId;
    private String name;
    private UUID parentId;
    private int displayOrder;
    private List<OrganizationResDto> children;

    public static OrganizationResDto fromEntity(Organization organization) {
        return OrganizationResDto.builder()
                .organizationId(organization.getOrganizationId())
                .name(organization.getName())
                .parentId(organization.getParent() != null ?
                        organization.getParent().getOrganizationId() : null)
                .displayOrder(organization.getDisplayOrder())
                .children(organization.getChildren() != null ?
                        organization.getChildren().stream()
                                .filter(child -> "NO".equals(child.getDelYn()))
                                .map(OrganizationResDto::fromEntity)
                                .collect(Collectors.toList()) : null)
                .build();
    }
}