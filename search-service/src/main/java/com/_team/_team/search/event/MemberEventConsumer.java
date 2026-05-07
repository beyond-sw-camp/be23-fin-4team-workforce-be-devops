package com._team._team.search.event;

import com._team._team.event.MemberDeletedEvent;
import com._team._team.event.MemberSavedEvent;
import com._team._team.event.OrganizationDeletedEvent;
import com._team._team.event.OrganizationSavedEvent;
import com._team._team.search.domain.MemberDocument;
import com._team._team.search.domain.OrganizationDocument;
import com._team._team.search.repository.MemberSearchRepository;
import com._team._team.search.repository.OrganizationSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEventConsumer {

    private final MemberSearchRepository memberSearchRepository;
    private final OrganizationSearchRepository organizationSearchRepository;

    @KafkaListener(topics = "member-saved", groupId = "search-service")
    public void consumeMemberSaved( MemberSavedEvent event) {
        try {
            // 1. 기존 멤버 조회 → 제거된 조직 처리
            memberSearchRepository
                    .findById(event.getMemberId().toString())
                    .ifPresent(existingMember -> {
                        List<OrganizationSavedEvent> existingOrgs =
                                existingMember.getOrganizationList();
                        List<OrganizationSavedEvent> eventOrgs =
                                event.getOrganizationList();

                        existingOrgs.stream()
                                .filter(existingOrg -> eventOrgs.stream()
                                        .noneMatch(eventOrg -> eventOrg
                                                .getOrganizationId()
                                                .equals(existingOrg.getOrganizationId())))
                                .forEach(removedOrg ->
                                        organizationSearchRepository
                                                .findById(removedOrg
                                                        .getOrganizationId()
                                                        .toString())
                                                .ifPresent(orgDoc -> {
                                                    orgDoc.getMemberList()
                                                            .removeIf(m -> m.getId()
                                                                    .equals(event.getMemberId()));
                                                    organizationSearchRepository
                                                            .save(orgDoc);
                                                }));
                    });

            // 2. suggest 생성
            List<String> orgNameList = event.getOrganizationList().stream()
                    .map(OrganizationSavedEvent::getName)
                    .collect(Collectors.toList());
            String suggestText = event.getName() + " "
                    + String.join(" ", orgNameList);

            // 3. MemberDocument 저장
            MemberDocument memberDocument = MemberDocument.builder()
                    .memberId(event.getMemberId().toString())
                    .companyId(event.getCompanyId().toString())
                    .name(event.getName())
                    .organizationList(event.getOrganizationList())
                    .titleName(event.getTitleName())
                    .phoneNumber(event.getPhoneNumber())
                    .email(event.getEmail())
                    .memberStatus(event.getMemberStatus())
                    .position(event.getPosition())
                    .suggest(new Completion(new String[]{suggestText}))
                    .build();

            memberSearchRepository.save(memberDocument);

            // 4. OrganizationDocument 저장/업데이트
            event.getOrganizationList().forEach(orgEvent -> {
                OrganizationDocument org = organizationSearchRepository
                        .findByCompanyIdAndLabel(
                                event.getCompanyId().toString(),
                                orgEvent.getName())
                        .orElseGet(() -> organizationSearchRepository.save(
                                OrganizationDocument.builder()
                                        .organizationId(orgEvent
                                                .getOrganizationId()
                                                .toString())
                                        .companyId(event.getCompanyId()
                                                .toString())
                                        .label(orgEvent.getName())
                                        .memberList(new ArrayList<>())
                                        .parentId(orgEvent.getParentId() != null
                                                ? orgEvent.getParentId().toString()
                                                : null)
                                        .build()));

                OrganizationDocument.Member member = org.getMemberList()
                        .stream()
                        .filter(m -> m.getId().equals(event.getMemberId()))
                        .findFirst()
                        .orElse(new OrganizationDocument.Member());

                member.updateMember(event);

                if (org.getMemberList().stream()
                        .noneMatch(m -> m.getId()
                                .equals(event.getMemberId()))) {
                    org.getMemberList().add(member);
                }

                organizationSearchRepository.save(org);
            });

            log.info("ES 저장 성공 memberId: {}", event.getMemberId());

        } catch (Exception e) {
            log.error("ES 저장 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "member-deleted", groupId = "search-service")
    public void consumeMemberDeleted(MemberDeletedEvent event) {
        try {


            // 1. OrganizationDocument에서 멤버 제거
            memberSearchRepository
                    .findById(event.getMemberId().toString())
                    .ifPresent(memberDoc ->
                            memberDoc.getOrganizationList()
                                    .forEach(org ->
                                            organizationSearchRepository
                                                    .findById(org.getOrganizationId()
                                                            .toString())
                                                    .ifPresent(orgDoc -> {
                                                        orgDoc.getMemberList()
                                                                .removeIf(m -> m.getId()
                                                                        .equals(event.getMemberId()));
                                                        organizationSearchRepository
                                                                .save(orgDoc);
                                                    })));

            // 2. MemberDocument 삭제
            memberSearchRepository.deleteById(
                    event.getMemberId().toString());

            log.info("ES 삭제 성공 memberId: {}", event.getMemberId());

        } catch (Exception e) {
            log.error("ES 삭제 실패: {}", e.getMessage());
        }
    }
    @KafkaListener(topics = "organization-deleted",
            groupId = "search-service")
    public void consumeOrganizationDeleted(
            OrganizationDeletedEvent event) {
        try {
            // ES에서 조직 삭제
            organizationSearchRepository.deleteById(
                    event.getOrganizationId().toString());

            log.info("조직 ES 삭제 성공 organizationId: {}",
                    event.getOrganizationId());

        } catch (Exception e) {
            log.error("조직 ES 삭제 실패: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "organization-saved",
            groupId = "search-service")
    public void consumeOrganizationSaved(
            OrganizationSavedEvent event) {
        try {
            // 기존 조직 있으면 업데이트 없으면 생성
            OrganizationDocument org = organizationSearchRepository
                    .findById(event.getOrganizationId().toString())
                    .orElse(OrganizationDocument.builder()
                            .organizationId(event.getOrganizationId().toString())
                            .companyId(event.getCompanyId().toString())
                            .memberList(new ArrayList<>())
                            .build());

            // 조직명 업데이트
            OrganizationDocument updatedOrg = OrganizationDocument.builder()
                    .organizationId(event.getOrganizationId().toString())
                    .companyId(event.getCompanyId().toString())
                    .label(event.getName())
                    .parentId(event.getParentId() != null
                            ? event.getParentId().toString()
                            : null)
                    .memberList(org.getMemberList())
                    .build();

            organizationSearchRepository.save(updatedOrg);

            log.info("조직 ES 저장 성공 organizationId: {}",
                    event.getOrganizationId());

        } catch (Exception e) {
            log.error("조직 ES 저장 실패: {}", e.getMessage());
        }
    }
}