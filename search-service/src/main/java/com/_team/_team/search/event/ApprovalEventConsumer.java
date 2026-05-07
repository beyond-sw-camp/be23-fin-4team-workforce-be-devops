package com._team._team.search.event;

import com._team._team.event.ApprovalDeletedEvent;
import com._team._team.event.ApprovalSavedEvent;
import com._team._team.search.domain.ApprovalDocument;
import com._team._team.search.repository.ApprovalSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEventConsumer {

    private final ApprovalSearchRepository approvalSearchRepository;

    @KafkaListener(topics = "approval-saved", groupId = "search-service")
    public void consumeApprovalSaved(ApprovalSavedEvent event) {
        try {
            ApprovalDocument doc = ApprovalDocument.builder()
                    .requestId(event.getRequestId().toString())
                    .companyId(event.getCompanyId().toString())
                    .memberId(event.getMemberId().toString())
                    .requesterName(event.getRequesterName())
                    .requesterOrganizationName(event.getRequesterOrganizationName())
                    .requesterOrganizationId(
                            event.getRequesterOrganizationId() != null
                                    ? event.getRequesterOrganizationId().toString()
                                    : null)
                    .documentName(event.getDocumentName())
                    .requestStatus(event.getRequestStatus())
                    .requestType(event.getRequestType())
                    .contentJson(event.getContentJson())
                    .createdAt(event.getCreatedAt())
                    .isDeptVisibleYn(event.getIsDeptVisibleYn())
                    .build();

            approvalSearchRepository.save(doc);

            log.info("결재 ES 저장 성공 requestId: {}", event.getRequestId());

        } catch (Exception e) {
            log.error("결재 ES 저장 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "approval-deleted", groupId = "search-service")
    public void consumeApprovalDeleted(ApprovalDeletedEvent event) {
        try {
            approvalSearchRepository.deleteById(event.getRequestId().toString());
            log.info("결재 ES 삭제 성공 requestId: {}", event.getRequestId());

        } catch (Exception e) {
            log.error("결재 ES 삭제 실패: {}", e.getMessage());
        }
    }
}