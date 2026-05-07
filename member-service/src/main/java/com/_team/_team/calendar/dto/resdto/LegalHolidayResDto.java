package com._team._team.calendar.dto.resdto;

import com._team._team.calendar.domain.LegalHoliday;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalHolidayResDto {

    private LocalDate holidayDate;
    private String holidayName;
    private int year;

    public static LegalHolidayResDto fromEntity(LegalHoliday legalHoliday) {
        return LegalHolidayResDto.builder()
                .holidayDate(legalHoliday.getHolidayDate())
                .holidayName(legalHoliday.getHolidayName())
                .year(legalHoliday.getYear())
                .build();
    }
}