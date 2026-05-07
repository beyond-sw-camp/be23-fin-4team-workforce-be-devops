package com._team._team.evaluation.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 시즌 활성화 422 응답 — 차단 멤버 ID(문자열 UUID) 목록 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonActivationBlockedBody {
    private List<String> weightShortageMembers;
    private List<String> pendingBundleMembers;
    private List<String> missingGoalsMembers;
}
