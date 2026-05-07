package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 휴가 신청 DTO */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LeaveRequestSubmitReqDto {

    @NotNull(message = "휴가 종류는 필수입니다.")
    private UUID companyLeaveTypeId;

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다.")
    private LocalDate endDate;

    @NotBlank(message = "휴가 사유는 필수입니다.")
    @Size(max = 500)
    private String reason;

    // 병가/경조 등 증빙 필요한 휴가일 때 필수, 그 외엔 null
    @Size(max = 500)
    private String evidenceFileUrl;

    /**
     * 비연속 사용 날짜 (예: ["2026-05-22", "2026-05-29"])
     * - 채워지면 startDate~endDate 범위 무시하고 이 날짜들만 사용일로 카운트
     */
    private List<LocalDate> plannedDates;
}