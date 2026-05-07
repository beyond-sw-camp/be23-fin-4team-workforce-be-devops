package com._team._team.meeting.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkGoalsReqDto {

    @NotNull(message = "연관 목표 ID 목록(JSON)은 필수입니다.")
    private String relatedGoalIdsJson;
}
