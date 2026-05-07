package com._team._team.contract.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractCancelReqDto {
    private String cancelReason;  // 회수 사유
}
