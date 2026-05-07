package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BundleRejectReqDto {

    @NotBlank(message = "반려 사유는 필수입니다.")
    @Size(max = 2000)
    private String reason;

    /** 권한자가 가리키는 문제 goal ID 들 (선택) */
    @Builder.Default
    private List<UUID> affectedGoalIds = new ArrayList<>();
}
