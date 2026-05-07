package com._team._team.meeting.dto.reqdto;

import com._team._team.meeting.domain.enums.RepeatCycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecordUpdateReqDto {

    private LocalDateTime scheduledAt;
    private String agenda;
    private RepeatCycle repeatCycle;
}
