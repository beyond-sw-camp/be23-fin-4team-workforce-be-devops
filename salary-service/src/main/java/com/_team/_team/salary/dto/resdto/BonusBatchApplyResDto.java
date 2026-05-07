package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 보너스 일괄 발행 결과
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BonusBatchApplyResDto {
    private int created;     // 신규 생성된 Payroll 건수
    private int failed;      // 실패 건수
    private List<UUID> payrollIds;
    private List<String> failures;  // 실패 사유 메시지 목록
}
