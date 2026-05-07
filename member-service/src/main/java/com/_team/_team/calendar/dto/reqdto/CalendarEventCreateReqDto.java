package com._team._team.calendar.dto.reqdto;

import com._team._team.calendar.domain.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventCreateReqDto {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "시작일시는 필수입니다.")
    private LocalDateTime startAt;

    @NotNull(message = "종료일시는 필수입니다.")
    private LocalDateTime endAt;

    private EventType eventType;  // PERSONAL, TEAM

    @Builder.Default
    private String isPublicYn = "YES";

    // TEAM 일정일 때 필수
    private UUID organizationId;
}
