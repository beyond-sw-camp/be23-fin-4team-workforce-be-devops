package com._team._team.esg.repository;

import com._team._team.esg.domain.EsgCampaign;
import com._team._team.esg.domain.EsgCampaignMember;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgCampaignMemberRepository extends JpaRepository<EsgCampaignMember, UUID> {

    boolean existsByCampaignAndMember(EsgCampaign campaign, Member member);

    Optional<EsgCampaignMember> findByCampaignAndMember(EsgCampaign campaign, Member member);

    List<EsgCampaignMember> findByCampaignOrderByJoinedAtDesc(EsgCampaign campaign);

    List<EsgCampaignMember> findByMemberOrderByJoinedAtDesc(Member member);

    // 캠페인 참여 인원 수 (정원 체크용)
    int countByCampaign(EsgCampaign campaign);
}