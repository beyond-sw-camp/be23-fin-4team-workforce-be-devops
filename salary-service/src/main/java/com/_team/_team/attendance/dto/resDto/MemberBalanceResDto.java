package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 잔여 응답 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberBalanceResDto {
    private UUID memberBalanceId;
    private UUID memberId;
    private BalanceType balanceType;
    private Double totalGranted;
    private Double totalUsed;
    private Double remaining;
    private LocalDate expirationDate;
    private String isUsableYn;
    private String isExpireYn;
    // 이월 동의 여부 / 회신 시각 - FE 가 동의 상태 표시에 사용
    private String carryoverConsentYn;
    private LocalDateTime carryoverConsentAt;

    public static MemberBalanceResDto fromEntity(MemberBalance mb) {
        return MemberBalanceResDto.builder()
                .memberBalanceId(mb.getMemberBalanceId())
                .memberId(mb.getMemberId())
                .balanceType(mb.getBalanceType())
                .totalGranted(mb.getTotalGranted())
                .totalUsed(mb.getTotalUsed())
                .remaining(mb.getRemaining())
                .expirationDate(mb.getExpirationDate())
                .isUsableYn(mb.getIsUsableYn())
                .isExpireYn(mb.getIsExpireYn())
                .carryoverConsentYn(mb.getCarryoverConsentYn())
                .carryoverConsentAt(mb.getCarryoverConsentAt())
                .build();
    }
}
