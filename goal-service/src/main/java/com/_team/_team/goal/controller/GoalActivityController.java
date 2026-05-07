package com._team._team.goal.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.goal.dto.resdto.GoalActivityResDto;
import com._team._team.goal.service.GoalActivityService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 목표 활동 조회
@RestController
@RequestMapping("/goal")
public class GoalActivityController {

    private final GoalActivityService goalActivityService;

    public GoalActivityController(GoalActivityService goalActivityService) {
        this.goalActivityService = goalActivityService;
    }

    // 목표 활동 조회
    @GetMapping("/{goalId}/activities")
    public ApiResponse<?> activities(
            @PathVariable UUID goalId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Page<GoalActivityResDto> p =
                    goalActivityService.listPage(goalId, UUID.fromString(memberId), UUID.fromString(companyId), page, size);
            Map<String, Object> body = new HashMap<>();
            body.put("content", p.getContent());
            body.put("totalElements", p.getTotalElements());
            body.put("totalPages", p.getTotalPages());
            body.put("number", p.getNumber());
            return ApiResponse.success(body, "목표 활동 내역 조회 성공(page)");
        }
        List<GoalActivityResDto> list =
                goalActivityService.list(goalId, UUID.fromString(memberId), UUID.fromString(companyId));
        return ApiResponse.success(list, "목표 활동 내역 조회 성공");
    }
}
