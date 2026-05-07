package com._team._team.attendance.dto.resDto;


import com._team._team.attendance.domain.CompanyHoliday;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompanyHolidayResDto {

    private UUID companyHolidayId;
    private UUID companyId;
    private LocalDate holidayDate;
    private String holidayName;
    private String isPaidYn;
    private String isLegalYn;

    public static CompanyHolidayResDto fromEntity(CompanyHoliday companyHoliday){
        return CompanyHolidayResDto.builder()
                .companyHolidayId(companyHoliday.getCompanyHolidayId())
                .companyId(companyHoliday.getCompanyId())
                .holidayDate(companyHoliday.getHolidayDate())
                .holidayName(companyHoliday.getHolidayName())
                .isPaidYn(companyHoliday.getIsPaidYn())
                .isLegalYn(companyHoliday.getIsLegalYn())
                .build();
    }
}
