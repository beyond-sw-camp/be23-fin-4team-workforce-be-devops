package com._team._team.calendar.dto.resdto;

import com._team._team.calendar.domain.CalendarEvent;
import com._team._team.calendar.domain.enums.EventType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventResDto {

    private UUID calendarEventId;
    private UUID memberId;
    private String memberName;
    private UUID organizationId;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private EventType eventType;
    private String eventTypeDescription;
    private String isPublicYn;
    private LocalDateTime createdAt;

    public static CalendarEventResDto fromEntity(CalendarEvent event) {
        return CalendarEventResDto.builder()
                .calendarEventId(event.getCalendarEventId())
                .memberId(event.getMember().getMemberId())
                .memberName(event.getMember().getName())
                .organizationId(event.getOrganizationId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .eventType(event.getEventType())
                .eventTypeDescription(event.getEventType().getDescription())
                .isPublicYn(event.getIsPublicYn())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
