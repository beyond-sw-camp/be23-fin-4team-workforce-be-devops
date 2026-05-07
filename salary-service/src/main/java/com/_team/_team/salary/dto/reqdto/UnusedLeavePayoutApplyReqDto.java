package com._team._team.salary.dto.reqdto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * 미사용 연차수당 확정 반영 요청 DTO
 * items: 담당자가 preview 후 금액 조정까지 완료한 대상 목록
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UnusedLeavePayoutApplyReqDto {
    private List<Item> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID payrollId;   // preview 결과에서 가져 온것
        private UUID memberId;    // 검증용
        private Long amount;      // 담당자가 조정한 최종 수당 금액
    }
}
