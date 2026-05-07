package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.CompanyHoliday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompanyHolidayCreateReqDto {

    @NotNull(message = "공휴일 날짜는 필수 입니다.")
    private LocalDate holidayDate;

    @NotBlank(message = "공휴일 이름은 필수 입니다.")
    private String holidayName;

    /** 유급 여부 (기본값 Y) */
    private String isPaidYn;

    public CompanyHoliday toEntity(UUID companyId){
        return CompanyHoliday.builder()
                .companyId(companyId)
                .holidayDate(this.holidayDate)
                .holidayName(this.holidayName)
                .isPaidYn(this.isPaidYn != null ? this.isPaidYn : "Y")
                .build();
    }
}
