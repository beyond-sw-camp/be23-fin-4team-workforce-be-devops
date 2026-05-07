package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 관리자 — 촉진 통보 이력 (회신 완료 / 강제 지정)
 *  - status = ACKNOWLEDGED: 직원 회신 plannedDates 기록
 *  - status = DESIGNATED  : 회사 강제 지정 designatedDates + 사유
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeavePromotionHistoryResDto {

    private UUID promotionLogId;
    private UUID memberId;
    private PromotionStage stage;
    private PromotionLogStatus status;
    private LocalDate sentOn;

    private LocalDate balanceExpirationDate;
    private Double remainingDays;

    /** 직원이 회신한 사용 예정일 (status=ACKNOWLEDGED) */
    private LocalDateTime acknowledgedAt;
    private List<String> plannedDates;

    /** 관리자가 강제 지정한 일자 (status=DESIGNATED) */
    private List<String> designatedDates;
    private String designationReason;

    public static LeavePromotionHistoryResDto from(LeavePromotionLog log,
                                                    LocalDate balanceExpirationDate,
                                                    Double remainingDays,
                                                    List<String> plannedDates,
                                                    List<String> designatedDates) {
        return LeavePromotionHistoryResDto.builder()
                .promotionLogId(log.getPromotionLogId())
                .memberId(log.getMemberId())
                .stage(log.getStage())
                .status(log.getStatus())
                .sentOn(log.getSentOn())
                .balanceExpirationDate(balanceExpirationDate)
                .remainingDays(remainingDays)
                .acknowledgedAt(log.getAcknowledgedAt())
                .plannedDates(plannedDates)
                .designatedDates(designatedDates)
                .designationReason(log.getDesignationReason())
                .build();
    }
}
