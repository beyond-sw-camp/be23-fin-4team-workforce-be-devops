package com._team._team.calendar.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventUpdateReqDto {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    private String description;

    @NotNull(message = "시작일시는 필수입니다.")
    private LocalDateTime startAt;

    @NotNull(message = "종료일시는 필수입니다.")
    private LocalDateTime endAt;

    private String isPublicYn;
}