package com._team._team.goal.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주기 단위 일괄 승인 요청.
 *
 * cycleKey 는 path param 으로 전달됨.
 * approverId 가 null 이면 MemberLookupClient.findDirectManagerId 로 자동 매핑.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitCycleReqDto {

    /** 1차 승인자 — null 이면 직속 조직장 자동 매핑 */
    private UUID approverId;

    @Builder.Default
    private List<UUID> watcherIds = new ArrayList<>();
}
