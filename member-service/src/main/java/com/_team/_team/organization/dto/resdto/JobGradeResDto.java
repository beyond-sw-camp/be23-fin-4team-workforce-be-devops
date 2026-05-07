package com._team._team.organization.dto.resdto;

import com._team._team.organization.domain.JobGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobGradeResDto {

    private UUID jobGradeId;
    private String name;
    private int displayOrder;

    public static JobGradeResDto fromEntity(JobGrade jobGrade) {
        return JobGradeResDto.builder()
                .jobGradeId(jobGrade.getJobGradeId())
                .name(jobGrade.getName())
                .displayOrder(jobGrade.getDisplayOrder())
                .build();
    }
}
