package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 연봉계약서 서명 완료 → salary-service 급여 반영 이벤트
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ContractSignedEvent {
    public static final String TOPIC = "contract-signed";

    private UUID companyId;
    private UUID memberId;
    private UUID contractId;
    private String contractType;

    private Long newSalary;
    private LocalDate effectiveDate;

    private LocalDateTime signedAt;
}
