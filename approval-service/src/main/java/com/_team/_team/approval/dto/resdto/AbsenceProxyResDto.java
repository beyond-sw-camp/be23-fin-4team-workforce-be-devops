package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.AbsenceProxy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AbsenceProxyResDto {
    private UUID proxyId;
    private UUID companyId;
    private UUID memberId;
    private UUID substituteId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String isActiveYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AbsenceProxyResDto fromEntity(AbsenceProxy entity) {
        return AbsenceProxyResDto.builder()
                .proxyId(entity.getProxyId())
                .companyId(entity.getCompanyId())
                .memberId(entity.getMemberId())
                .substituteId(entity.getSubstituteId())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .isActiveYn(entity.getIsActiveYn())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
