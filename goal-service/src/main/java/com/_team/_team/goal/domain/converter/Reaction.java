package com._team._team.goal.domain.converter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 댓글 리액션 값 객체.
 * JSON 형태: {"emoji":"👍","memberIds":["uuid1","uuid2"]}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {
    private String emoji;
    private List<UUID> memberIds = new ArrayList<>();

    /**
     * 토글: memberId가 이미 있으면 제거, 없으면 추가.
     */
    public boolean toggle(UUID memberId) {
        if (memberIds.contains(memberId)) {
            memberIds.remove(memberId);
            return false;
        } else {
            memberIds.add(memberId);
            return true;
        }
    }

    public boolean isEmpty() {
        return memberIds == null || memberIds.isEmpty();
    }
}
