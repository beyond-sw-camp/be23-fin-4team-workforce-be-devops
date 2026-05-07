package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgScore;
import com._team._team.esg.domain.enums.EsgGrade;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgScoreResDto {

    private UUID esgScoreId;
    private String yearMonth;
    private int eScore;
    private int sScore;
    private int gScore;
    private int totalScore;
    private EsgGrade grade;
    private String gradeDescription;

    public static EsgScoreResDto fromEntity(EsgScore score) {
        return EsgScoreResDto.builder()
                .esgScoreId(score.getEsgScoreId())
                .yearMonth(score.getYearMonth())
                .eScore(score.getEScore())
                .sScore(score.getSScore())
                .gScore(score.getGScore())
                .totalScore(score.getTotalScore())
                .grade(score.getGrade())
                .gradeDescription(score.getGrade().getDescription())
                .build();
    }
}
