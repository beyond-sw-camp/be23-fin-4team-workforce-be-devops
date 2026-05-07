package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 일괄 확정 일괄 지급 처리 결과
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BulkPayrollActionResDto {

    private int success;
    private int fail;
    private List<String> failures;
}
