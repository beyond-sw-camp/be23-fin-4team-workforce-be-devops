package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.NegotiationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 일괄 협상 등록 요청 (정기 시즌)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryNegotiationBulkCreateReqDto {

    @NotNull(message = "그룹명 필수")
    private String groupName;

    @NotNull(message = "협상 종류 필수")
    private NegotiationType negotiationType;

    @NotNull(message = "적용 시작일 필수")
    private LocalDate proposedEffectiveFrom;

    @NotEmpty(message = "대상 직원 목록은 비어있을 수 없습니다.")
    @Valid
    private List<Item> items;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Item {
        @NotNull
        private UUID memberId;

        @NotNull(message = "제안 기본급 필수")
        private Long proposedBaseSalary;

        private String proposedJobGradeName;
        private String proposedJobTitleName;
        private String reason;
    }
}
