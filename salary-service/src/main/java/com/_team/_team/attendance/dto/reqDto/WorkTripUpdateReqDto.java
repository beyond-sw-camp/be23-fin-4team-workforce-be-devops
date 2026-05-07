package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.ExpenseType;
import com._team._team.attendance.domain.enums.WorkTripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 출장/외근 수정 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkTripUpdateReqDto {
    private WorkTripType workTripType;
    private String destination;
    private String purpose;
    private Long expenseAmount;
    private ExpenseType expenseType;
}
