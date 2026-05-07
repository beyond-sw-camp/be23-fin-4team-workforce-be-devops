package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionStage;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// 관리자 무응답자 리스트 단건, 직원 이름 같은 부가 정보는 별도 조회 후 합쳐서 응답
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeavePromotionNoResponseResDto {

    private UUID promotionLogId;
    private UUID memberId;
    private PromotionStage stage;
    private LocalDate sentOn;
    private LocalDate balanceExpirationDate;
    private Double remainingDays;
    private long daysSinceSent;

    public static LeavePromotionNoResponseResDto from(LeavePromotionLog log,
                                                      LocalDate balanceExpirationDate,
                                                      Double remainingDays,
                                                      long daysSinceSent) {
        return LeavePromotionNoResponseResDto.builder()
                .promotionLogId(log.getPromotionLogId())
                .memberId(log.getMemberId())
                .stage(log.getStage())
                .sentOn(log.getSentOn())
                .balanceExpirationDate(balanceExpirationDate)
                .remainingDays(remainingDays)
                .daysSinceSent(daysSinceSent)
                .build();
    }
}