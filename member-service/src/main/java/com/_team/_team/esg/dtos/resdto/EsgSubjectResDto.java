package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgActivitySubject;
import com._team._team.esg.domain.enums.EsgCategory;
import com._team._team.esg.domain.enums.VerificationType;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgSubjectResDto {

    private UUID esgActivitySubjectId;
    private String title;
    private String description;
    private EsgCategory category;
    private String categoryDescription;
    private int defaultPoints;

    public static EsgSubjectResDto fromEntity(EsgActivitySubject subject) {
        return EsgSubjectResDto.builder()
                .esgActivitySubjectId(subject.getEsgActivitySubjectId())
                .title(subject.getTitle())
                .description(subject.getDescription())
                .category(subject.getCategory())
                .categoryDescription(subject.getCategory().getDescription())
                .defaultPoints(subject.getDefaultPoints())
                .build();
    }
}