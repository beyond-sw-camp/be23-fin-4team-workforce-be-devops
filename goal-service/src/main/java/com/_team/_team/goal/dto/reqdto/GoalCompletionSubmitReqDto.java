package com._team._team.goal.dto.reqdto;

import lombok.Data;

import java.util.UUID;

/**
 * 완료 승인 요청 DTO.
 *
 * {@code approverId} 는 optional 이다:
 *   - 목표 생성 시 지정한 {@code Goal.completionApproverId} 를 기본값으로 사용
 *   - DTO 로 값을 주면 오버라이드 (UI 에서 "다른 사람으로 변경" 허용 시)
 *   - 둘 다 null 이면 400
 */
@Data
public class GoalCompletionSubmitReqDto {
    /** 선택: 없으면 Goal 엔티티의 completionApproverId 사용. */
    private UUID approverId;

    private String summary;
    private String evidenceFiles;
}

