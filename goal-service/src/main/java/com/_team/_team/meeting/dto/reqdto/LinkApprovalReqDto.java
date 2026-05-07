package com._team._team.meeting.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkApprovalReqDto {

    @NotNull(message = "결재 ID는 필수입니다.")
    private UUID approvalId;
}
