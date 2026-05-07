package com._team._team.approval.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AbsenceProxyCreateReqDto {

    @NotNull(message = "대결자 ID는 필수입니다.")
    private UUID substituteId;

    @NotNull(message = "위임 시작일시는 필수입니다.")
    private LocalDateTime startDate;

    @NotNull(message = "위임 종료일시는 필수입니다.")
    private LocalDateTime endDate;
}
