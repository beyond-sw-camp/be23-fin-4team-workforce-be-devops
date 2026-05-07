package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.BalanceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 휴가 차감/복구 요청 DTO
 *
 * ===== 차감 시나리오 (useBalance) =====
 * 사원이 연차 신청 → approval-service에서 승인 → Kafka 이벤트 수신
 * → 이 DTO로 MemberBalance.use(days) 호출
 *
 * ===== 복구 시나리오 (restoreBalance) =====
 * 승인된 연차가 반려/취소 → Kafka 이벤트 수신
 * → 이 DTO로 MemberBalance.restore(days) 호출
 *
 * ===== 연차-근태 연동 (1차 추가) =====
 * leaveDate가 있으면 DailyAttendance.status를 LEAVE/HALF로 자동 변경
 * leaveDate가 null이면 기존 동작 유지 (하위 호환)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberBalanceUseReqDto {

    @NotNull(message = "잔여 유형은 필수입니다.")
    private BalanceType balanceType;

    @NotNull(message = "차감/복구 일수는 필수입니다.")
    @Positive(message = "일수는 0보다 커야 합니다.")
    private Double days;

    /**
     * 연차 대상 날짜
     * days >= 1.0이면 해당 날짜 status = LEAVE (전일 휴가)
     * days == 0.5이면 해당 날짜 status = HALF (반차)
     */
    private LocalDate leaveDate;
}
