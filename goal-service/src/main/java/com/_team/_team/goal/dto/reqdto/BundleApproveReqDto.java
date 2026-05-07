package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BundleApproveReqDto {

    @Size(max = 1000)
    private String comment;
}
