package com._team._team.approval.service;

import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.repository.ApprovalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OvertimeQueryService {

    private final ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    public OvertimeQueryService(ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
    }

    // 해당 날짜에 승인받은 연장근무 중 가장 늦은 종료시각 반환, 없으면 null
    // salary-service가 근태 자동 마감할 때 FeignClient 통해 호출
    public LocalDateTime findLatestApprovedEndAt(UUID memberId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();

        return approvalRequestRepository
                .findLatestApprovedOvertimeEnd(
                        memberId, RequestStatus.APPROVED, startOfDay, startOfNextDay)
                .orElse(null);
    }
}