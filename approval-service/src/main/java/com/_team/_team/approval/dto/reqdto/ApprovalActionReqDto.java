package com._team._team.approval.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalActionReqDto {
    private String comment; // 승인 시 선택, 반려 시 필수
}
