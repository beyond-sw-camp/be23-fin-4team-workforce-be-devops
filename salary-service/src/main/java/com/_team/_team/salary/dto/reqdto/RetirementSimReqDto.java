package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 직원 본인 퇴직금 시뮬
// joinDate 는 프론트가 본인 정보에서 채워 보냄 Feign 호출 제거 안정성 확보
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RetirementSimReqDto {

    private LocalDate joinDate;

    @NotNull(message = "예상 퇴직일은 필수입니다.")
    private LocalDate resignDate;
}