package com._team._team.member.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.dto.resdto.MemberPositionResDto;
import com._team._team.member.repository.MemberPositionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/member/position")
public class MemberPositionController {

    private final MemberPositionRepository memberPositionRepository;


    @Autowired
    public MemberPositionController(MemberPositionRepository memberPositionRepository) {
        this.memberPositionRepository = memberPositionRepository;
    }
    // memberPositionId로 상세 조회
    @GetMapping("/internal/{memberPositionId}")
    public MemberPositionResDto getMemberPositionInternal(
            @PathVariable UUID memberPositionId) {
        MemberPosition mp = memberPositionRepository.findActiveByIdWithDetails(memberPositionId)
                .orElseThrow(() -> new EntityNotFoundException("해당 직위 정보를 찾을 수 없습니다."));
        return MemberPositionResDto.fromEntity(mp);
    }

    // 부서 + 직책으로 조회
    @GetMapping("/internal/search/by-job-title")
    public List<MemberPositionResDto> searchByJobTitleInternal(
            @RequestParam UUID organizationId,
            @RequestParam UUID jobTitleId) {
        return memberPositionRepository
                .findByOrganizationIdAndJobTitleId(organizationId, jobTitleId)
                .stream()
                .map(MemberPositionResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 부서 + 직급으로 조회
    @GetMapping("/search/by-job-grade")
    public ResponseEntity<?> searchByJobGrade(
            @RequestParam UUID organizationId,
            @RequestParam UUID jobGradeId) {
        List<MemberPositionResDto> result = memberPositionRepository
                .findByOrganizationIdAndJobGradeId(organizationId, jobGradeId)
                .stream()
                .map(MemberPositionResDto::fromEntity)
                .collect(Collectors.toList());
        return new ResponseEntity<>(
                ApiResponse.success(result, "직급 기반 조회 성공"),
                HttpStatus.OK
        );
    }

}
