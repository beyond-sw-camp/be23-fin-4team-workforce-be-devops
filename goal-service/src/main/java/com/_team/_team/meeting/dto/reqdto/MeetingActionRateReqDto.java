package com._team._team.meeting.dto.reqdto;

import com._team._team.meeting.domain.enums.TlRating;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingActionRateReqDto {

    @NotNull(message = "TL 평가는 필수입니다.")
    private TlRating tlRating;
}
