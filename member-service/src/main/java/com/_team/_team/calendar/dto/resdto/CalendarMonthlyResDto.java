package com._team._team.calendar.dto.resdto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarMonthlyResDto {

    private List<CalendarEventResDto> events;
    private List<HolidayDto> holidays;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HolidayDto {
        private LocalDate holidayDate;
        private String holidayName;
    }
}