package com._team._team.memberchat.service;

import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.repository.MemberPositionRepository;
import com._team._team.member.repository.MemberRepository;
import com._team._team.memberchat.dto.res.ChatSenderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 채팅방과 같은 회사에 속한 발신자에 대해 이름·프로필·직책·소속을 조회한다.
 * (채팅 API는 이미 방 참가자만 열람 가능하므로, 표시용 메타는 서버 내부 조회로 충분하다.)
 */
@Component
@RequiredArgsConstructor
public class ChatMessageSenderEnricher {

    private final MemberRepository memberRepository;
    private final MemberPositionRepository memberPositionRepository;

    public Map<UUID, ChatSenderView> loadSenders(UUID companyId, Collection<UUID> senderIds) {
        Map<UUID, ChatSenderView> out = new HashMap<>();
        if (senderIds == null || senderIds.isEmpty() || companyId == null) {
            return out;
        }
        Set<UUID> ids = senderIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return out;
        }
        for (Member member : memberRepository.findAllById(ids)) {
            enrichOne(companyId, member, out);
        }
        return out;
    }

    private void enrichOne(UUID companyId, Member member, Map<UUID, ChatSenderView> out) {
        if (!companyId.equals(member.getCompany().getCompanyId())) {
            return;
        }
        if (!"NO".equals(member.getDelYn())) {
            return;
        }
        String name = member.getName();
        String profileUrl = member.getProfileUrl();
        String jobTitle = null;
        String jobGrade = null;
        String orgName = null;
        UUID posId = member.getDefaultPositionId();
        if (posId != null) {
            var posOpt = memberPositionRepository.findByIdWithDetails(posId);
            if (posOpt.isPresent()) {
                MemberPosition p = posOpt.get();
                if (p.getJobTitle() != null) {
                    jobTitle = p.getJobTitle().getName();
                }
                if (p.getJobGrade() != null) {
                    jobGrade = p.getJobGrade().getName();
                }
                if (p.getOrganization() != null) {
                    orgName = p.getOrganization().getName();
                }
            }
        }
        out.put(member.getMemberId(), new ChatSenderView(name, profileUrl, jobTitle, jobGrade, orgName));
    }
}
