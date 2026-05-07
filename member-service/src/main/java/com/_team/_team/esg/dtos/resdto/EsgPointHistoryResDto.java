package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgPointHistory;
import com._team._team.esg.domain.enums.PointType;
import com._team._team.esg.domain.enums.ReferenceType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgPointHistoryResDto {

    private UUID esgPointHistoryId;
    private PointType pointType;
    private ReferenceType referenceType;
    private String referenceTypeDescription;
    private int points;
    private int balance;
    private String description;
    private LocalDateTime createdAt;

    public static EsgPointHistoryResDto fromEntity(EsgPointHistory history) {
        return EsgPointHistoryResDto.builder()
                .esgPointHistoryId(history.getEsgPointHistoryId())
                .pointType(history.getPointType())
                .referenceType(history.getReferenceType())
                .referenceTypeDescription(history.getReferenceType().getDescription())
                .points(history.getPoints())
                .balance(history.getBalance())
                .description(history.getDescription())
                .createdAt(history.getCreatedAt())
                .build();
    }
}