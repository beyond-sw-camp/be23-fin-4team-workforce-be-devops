package com._team._team.contract.feignclients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SalaryApiResponse {
    private boolean success;
    private String message;
    private List<SalaryInfoResDto> data;
}
