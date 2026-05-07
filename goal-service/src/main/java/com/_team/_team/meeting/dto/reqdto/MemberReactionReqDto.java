package com._team._team.meeting.dto.reqdto;

import com._team._team.meeting.domain.enums.Reaction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberReactionReqDto {

    @NotNull(message = "참여자 반응은 필수입니다.")
    private Reaction memberReaction;
}
