package com._team._team.member.consumer;

import com._team._team.event.ResignationApprovalEvent;
import com._team._team.member.domain.Member;
import com._team._team.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 사직서 결재 결과 Kafka 수신 -> Member 상태/퇴직일 반영
 */
@Slf4j
@Component
public class MemberStatusResignationConsumer {

    private final MemberRepository memberRepository;

    public MemberStatusResignationConsumer(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @KafkaListener(
            topics = ResignationApprovalEvent.TOPIC,
            groupId = "member-service-resignation",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE +
                            "=com._team._team.event.ResignationApprovalEvent"
            }
    )
    @Transactional
    public void consume(ResignationApprovalEvent event) {
        log.info("[Resignation] received. requestId={}, action={}, memberId={}, resignDate={}",
                event.getRequestId(), event.getAction(),
                event.getMemberId(), event.getResignDate());

        if (event.getAction() != ResignationApprovalEvent.Action.APPROVE) {
            log.info("[Resignation] non-approve action - skipped. action={}", event.getAction());
            return;
        }

        if (event.getMemberId() == null) {
            log.warn("[Resignation] memberId null - skipped. requestId={}", event.getRequestId());
            return;
        }

        Member member = memberRepository.findById(event.getMemberId()).orElse(null);
        if (member == null) {
            log.warn("[Resignation] member not found. memberId={}", event.getMemberId());
            return;
        }

        // 퇴직 희망일이 비어있으면 오늘
        LocalDate retireDate = event.getResignDate() != null
                ? event.getResignDate()
                : LocalDate.now();

        member.markResigned(retireDate);
        log.info("[Resignation] member status -> LEAVE. memberId={}, retireDate={}",
                event.getMemberId(), retireDate);
    }
}
