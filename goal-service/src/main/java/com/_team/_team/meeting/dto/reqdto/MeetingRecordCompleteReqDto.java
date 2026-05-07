package com._team._team.meeting.dto.reqdto;

import com._team._team.meeting.domain.enums.Reaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecordCompleteReqDto {

    private String memo;
    private String privateMemo;
    private Reaction managerReaction;
}
