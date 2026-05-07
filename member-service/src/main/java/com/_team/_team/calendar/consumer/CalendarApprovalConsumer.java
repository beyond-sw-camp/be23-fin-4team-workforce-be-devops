package com._team._team.calendar.consumer;

import com._team._team.calendar.domain.CalendarEvent;
import com._team._team.calendar.domain.enums.EventType;
import com._team._team.calendar.repository.CalendarEventRepository;
import com._team._team.event.CalendarApprovalEvent;
import com._team._team.member.domain.Member;
import com._team._team.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class CalendarApprovalConsumer {
    private final CalendarEventRepository calendarEventRepository;
    private final MemberRepository memberRepository;

    public CalendarApprovalConsumer(CalendarEventRepository calendarEventRepository,
                                    MemberRepository memberRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.memberRepository = memberRepository;
    }

    @KafkaListener(topics = "approval-calendar", groupId = "member-service-calendar",
    containerFactory = "calendarKafkaListenerContainerFactory")
    @Transactional
    public void consume(CalendarApprovalEvent event) {
        try {
            // 중복 체크
            if (calendarEventRepository.existsByReferenceId(event.getRequestId())) {
                log.info("[Calendar] 이미 등록된 일정. requestId={}", event.getRequestId());
                return;
            }

            Member member = memberRepository.findById(event.getMemberId())
                    .orElse(null);
            if (member == null) {
                log.warn("[Calendar] 멤버 조회 실패. memberId={}", event.getMemberId());
                return;
            }

            CalendarEvent calendarEvent = CalendarEvent.builder()
                    .company(member.getCompany())
                    .member(member)
                    .organizationId(event.getOrganizationId())
                    .title(event.getTitle())
                    .description(null)
                    .startAt(event.getStartAt())
                    .endAt(event.getEndAt())
                    .eventType(EventType.APPROVAL)
                    .isPublicYn("YES")
                    .referenceId(event.getRequestId())
                    .build();

            calendarEventRepository.save(calendarEvent);
            log.info("[Calendar] 결재 일정 생성 완료. requestId={}, title={}",
                    event.getRequestId(), event.getTitle());

        } catch (Exception e) {
            log.error("[Calendar] 결재 일정 생성 실패. requestId={}",
                    event.getRequestId(), e);
        }
    }
}
