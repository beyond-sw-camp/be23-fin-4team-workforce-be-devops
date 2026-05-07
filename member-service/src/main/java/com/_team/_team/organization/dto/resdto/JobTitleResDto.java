package com._team._team.organization.dto.resdto;

import com._team._team.organization.domain.JobTitle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTitleResDto {

    private UUID jobTitleId;
    private String name;
    private int displayOrder;

    public static JobTitleResDto fromEntity(JobTitle jobTitle) {
        return JobTitleResDto.builder()
                .jobTitleId(jobTitle.getJobTitleId())
                .name(jobTitle.getName())
                .displayOrder(jobTitle.getDisplayOrder())
                .build();
    }
}