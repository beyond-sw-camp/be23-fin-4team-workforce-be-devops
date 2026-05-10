package com._team._team.salary.consumer;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.domain.enums.WorkTripType;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.WorkTripDetailRepository;
import com._team._team.event.BusinessTripApprovalEvent;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.service.MemberAllowanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 출장 결재 이벤트 Consumer
 */
@Slf4j
@Component
public class BusinessTripApprovalEventConsumer {

    private static final String TRIP_ALLOWANCE_ITEM_NAME = "출장수당";

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final WorkTripDetailRepository workTripDetailRepository;

    @Autowired
    public BusinessTripApprovalEventConsumer(MemberAllowanceService memberAllowanceService,
                                             SalaryItemTemplateRepository salaryItemTemplateRepository,
                                             DailyAttendanceRepository dailyAttendanceRepository,
                                             WorkTripDetailRepository workTripDetailRepository) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.workTripDetailRepository = workTripDetailRepository;
    }

    @KafkaListener(
            topics = BusinessTripApprovalEvent.TOPIC,
            groupId = "salary-service",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.BusinessTripApprovalEvent"
            }
    )
    public void consume(BusinessTripApprovalEvent event) {
        log.info("[BusinessTrip] event received. requestId={} action={} memberId={} total={} period={}~{}",
                event.getRequestId(), event.getAction(), event.getMemberId(), event.getTotalAmount(),
                event.getTripStartDate(), event.getTripEndDate());

        if (event.getAction() != BusinessTripApprovalEvent.Action.APPROVE) {
            log.info("[BusinessTrip] APPROVE 외 액션은 skip. action={}", event.getAction());
            return;
        }

        // 출장수당 자동 부여 흐름 비활성화
        // 근태 반영만 유지 - 출장 기간 매일에 대해 DailyAttendance + WorkTripDetail row 생성
        try {
            reflectAttendance(event);
        } catch (Exception e) {
            log.warn("[BusinessTrip] 근태 반영 실패. memberId={}", event.getMemberId(), e);
        }
    }

    private void reflectAttendance(BusinessTripApprovalEvent event) {
        if (event.getTripStartDate() == null || event.getTripEndDate() == null) return;

        LocalDate cursor = event.getTripStartDate();
        int created = 0;
        while (!cursor.isAfter(event.getTripEndDate())) {
            final LocalDate date = cursor;
            // 그 날 DailyAttendance 가 없으면 새로 생성, 있으면 그대로 사용
            DailyAttendance daily = dailyAttendanceRepository
                    .findByCompanyIdAndMemberIdAndAttendanceDate(event.getCompanyId(), event.getMemberId(), date)
                    .orElseGet(() -> dailyAttendanceRepository.save(
                            DailyAttendance.builder()
                                    .memberId(event.getMemberId())
                                    .companyId(event.getCompanyId())
                                    .attendanceDate(date)
                                    .build()));

            // WorkTripDetail 체크
            boolean exists = !workTripDetailRepository
                    .findByMemberIdAndCompanyIdAndDelYnAndDailyAttendanceDailyAttendanceId(
                            event.getMemberId(), event.getCompanyId(), "N", daily.getDailyAttendanceId())
                    .isEmpty();
            if (!exists) {
                workTripDetailRepository.save(WorkTripDetail.builder()
                        .memberId(event.getMemberId())
                        .companyId(event.getCompanyId())
                        .workTripType(WorkTripType.BUSINESS_TRIP)
                        .dailyAttendance(daily)
                        .build());
                created++;
            }
            cursor = cursor.plusDays(1);
        }
        log.info("[BusinessTrip] 근태 반영 완료. memberId={} created={}", event.getMemberId(), created);
    }
}
