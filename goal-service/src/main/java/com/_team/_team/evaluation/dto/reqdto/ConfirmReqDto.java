package com._team._team.evaluation.dto.reqdto;

import com._team._team.goal.domain.enums.Grade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmReqDto {

    private Grade confirmedGrade;

    private String comment;
}
