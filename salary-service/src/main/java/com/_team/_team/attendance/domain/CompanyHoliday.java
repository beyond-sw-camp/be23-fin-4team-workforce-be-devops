package com._team._team.attendance.domain;


import com._team._team.attendance.dto.reqDto.CompanyHolidayUpdateReqDto;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 회사 공휴일
 * - 법정 공휴일 + 회사 지정 휴일 (창립기념일 등)
 * - isPaidYn: 유급/무급 구분 (급여 계산 시 salary-service에서 참조)
 * - 월간 근태표에서 공휴일 표시용으로 조회
 * - (company_id, holiday_date) UNIQUE — 같은 날 중복 등록 방지
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Builder
public class CompanyHoliday extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID companyHolidayId;

    @Column(nullable = false)
    private UUID companyId;

    /** 공휴일 날짜 */
    @Column(nullable = false)
    private LocalDate holidayDate;

    /** 공휴일 이름 (ex: 설날, 추석, 창립기념일) */
    @Column(nullable = false, length = 100)
    private String holidayName;

    /** 법정 공휴일 여부 */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isLegalYn = "N";

    /** 유급 여부 (Y: 유급휴일 / N: 무급휴일) */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isPaidYn = "Y";

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String delYn = "N";

    /**
     * 공휴일 정보 수정
     * - null이 아닌 필드만 수정 (부분 업데이트)
     */
    public void update(CompanyHolidayUpdateReqDto reqDto) {
        if (reqDto.getHolidayName() != null) {
            this.holidayName = reqDto.getHolidayName();
        }
        if (reqDto.getHolidayDate() != null) {
            this.holidayDate = reqDto.getHolidayDate();
        }
        if (reqDto.getIsPaidYn() != null) {
            this.isPaidYn = reqDto.getIsPaidYn();
        }
    }

    public void delete(){
        this.delYn = "Y";
    }
}
