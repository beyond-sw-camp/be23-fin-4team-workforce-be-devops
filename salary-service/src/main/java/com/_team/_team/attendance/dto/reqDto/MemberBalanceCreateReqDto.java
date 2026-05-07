package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;


/**
 * 휴가 부여 요청 DTO
 * HR 관리자가 사원에게 연차를 부여할 때 사용
 * - 매년 1/1 (또는 입사일) 기준으로 연차 부여
 * - 이월 연차 별도 부여 가능
 *
 * memberId      : 부여 대상 사원 (관리자가 지정)
 * balanceType   : ANNUAL(당해 연차) / CARRYOVER(이월 연차) / MONTHLY: 월차
 * totalGranted  : 부여 일수 (ex: 15.0)
 * expirationDate: 만료일 (이 날짜 이후 배치에서 isExpireYn='Y' 처리)
 *                 ANNUAL → 보통 다음해 12/31
 *                 CARRYOVER → leave_policy에 따라 다르게 처리
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberBalanceCreateReqDto {

    @NotNull(message = "대상 사원 ID는 필수 입니다.")
    private UUID memberId;

    @NotNull(message = "잔여 유형은 필수 입니다.")
    private BalanceType balanceType;

    @NotNull(message = "부여 일수는 필수입니다.")
    @Positive(message = "부여 일수는 0보다 커야 합니다.")
    private Double totalGranted;

    private LocalDate expirationDate;

    public MemberBalance toEntity(UUID companyId){
        return MemberBalance.builder()
                .memberId(this.memberId)
                .companyId(companyId)
                .balanceType(this.balanceType)
                .totalGranted(this.totalGranted)
                .totalUsed(0.0)
                .remaining(this.totalGranted)
                .expirationDate(this.expirationDate)
                .build();
    }
}
