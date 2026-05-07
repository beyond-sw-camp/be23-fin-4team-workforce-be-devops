package com._team._team.attendance.dto.resDto;

import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LegalHolidayResDto {

    private LocalDate holidayDate;
    private String holidayName;
    private int year;
}