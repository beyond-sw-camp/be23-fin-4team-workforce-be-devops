package com._team._team.meeting.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingActionCreateReqDto {

    /**
     * [M2] 담당자는 반드시 지정되어야 합니다. "누가 할 일인지" 가 빠진 액션 아이템은
     * 추적이 불가능하므로 API 레벨에서부터 차단합니다.
     */
    @NotNull(message = "담당자(assigneeId)는 필수입니다.")
    private UUID assigneeId;

    @NotBlank(message = "액션 설명은 필수입니다.")
    @Size(max = 500)
    private String description;

    private LocalDate dueDate;
}
