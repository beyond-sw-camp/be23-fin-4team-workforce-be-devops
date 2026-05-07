package com._team._team.member.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 내부 Feign 호출용 최소 프로필.
 * 평가·목표·면담 등 다른 도메인에서 대상자/평가자 이름 + 소속을 노출할 때 사용한다.
 *
 * department : 소속 조직 이름 (null 가능 — 포지션 미지정 등)
 * positionName : 직책/직급 이름 (null 가능)
 * profileUrl : 프로필 이미지 URL (null 가능)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberMinimalProfileResDto {
    private String name;
    private String department;
    private String positionName;
    private String profileUrl;
}
