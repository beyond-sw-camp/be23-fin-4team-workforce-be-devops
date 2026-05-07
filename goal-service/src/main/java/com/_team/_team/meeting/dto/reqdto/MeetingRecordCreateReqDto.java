package com._team._team.meeting.dto.reqdto;

import com._team._team.meeting.domain.enums.RepeatCycle;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecordCreateReqDto {

    private UUID parentRecordId;

    @NotNull(message = "멤버 ID는 필수입니다.")
    private UUID memberId;

    @NotNull(message = "매니저 ID는 필수입니다.")
    private UUID managerId;

    @NotNull(message = "반복 주기는 필수입니다.")
    private RepeatCycle repeatCycle;

    @NotNull(message = "예정 일시는 필수입니다.")
    private LocalDateTime scheduledAt;

    private String agenda;
}
