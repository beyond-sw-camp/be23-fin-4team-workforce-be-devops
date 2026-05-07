package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * 휴직 신청 저장 후 approval-service 결재 생성 시 결재 ID 를 salary 에 연결
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveOfAbsenceLinkApprovalReqDto {

    @NotNull(message = "결재 요청 ID 는 필수입니다.")
    private UUID approvalRequestId;
}
