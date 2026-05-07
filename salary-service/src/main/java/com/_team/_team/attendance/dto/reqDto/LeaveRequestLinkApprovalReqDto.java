package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveRequestLinkApprovalReqDto {

    @NotNull(message = "결재 요청 ID 는 필수입니다.")
    private UUID approvalRequestId;
}