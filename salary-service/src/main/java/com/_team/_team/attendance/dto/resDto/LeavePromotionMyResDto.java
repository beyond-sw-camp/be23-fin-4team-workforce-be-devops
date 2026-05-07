package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
// 직원 본인이 받은 촉진 통보 단건 회신 내역 표시용 plannedDates designatedDates 노출
public class LeavePromotionMyResDto {
    private UUID promotionLogId;
    private PromotionStage stage;
    private PromotionLogStatus status;
    private LocalDate sentOn;
    private LocalDateTime acknowledgedAt;
    // 직원이 처음 통보를 열람한 시각
    private LocalDateTime viewedAt;
    private LocalDate balanceExpirationDate;
    private Double remainingDays;

    // 직원이 회신한 사용 계획 날짜 ACKNOWLEDGED 일 때만 비어있지 않음
    private List<String> plannedDates;

    // 회사가 강제 지정한 연차일 DESIGNATED 일 때만 비어있지 않음
    private List<String> designatedDates;

    // 강제 지정 사유
    private String designationReason;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static LeavePromotionMyResDto from(LeavePromotionLog log,
                                              LocalDate balanceExpirationDate,
                                              Double remainingDays) {
        return LeavePromotionMyResDto.builder()
                .promotionLogId(log.getPromotionLogId())
                .stage(log.getStage())
                .status(log.getStatus())
                .sentOn(log.getSentOn())
                .acknowledgedAt(log.getAcknowledgedAt())
                .viewedAt(log.getViewedAt())
                .balanceExpirationDate(balanceExpirationDate)
                .remainingDays(remainingDays)
                .plannedDates(parseDates(log.getPlannedDates()))
                .designatedDates(parseDates(log.getDesignatedDates()))
                .designationReason(log.getDesignationReason())
                .build();
    }

    // JSON 배열 문자열 List<String> 파싱 잘못된 데이터일 경우 빈 리스트
    private static List<String> parseDates(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
