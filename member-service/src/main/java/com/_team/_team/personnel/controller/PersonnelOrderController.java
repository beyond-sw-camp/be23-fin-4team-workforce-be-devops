package com._team._team.personnel.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.personnel.dto.PersonnelOrderResDto;
import com._team._team.personnel.repository.PersonnelOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 인사발령 이력 조회
 */
@RestController
@RequestMapping("/personnel-order")
@RequiredArgsConstructor
public class PersonnelOrderController {

    private final PersonnelOrderRepository repository;

    @GetMapping("/my")
    public ResponseEntity<?> findMy(@RequestHeader("X-User-UUID") UUID memberId) {
        List<PersonnelOrderResDto> data = repository
                .findByMemberIdOrderByEffectiveDateDescCreatedAtDesc(memberId)
                .stream().map(PersonnelOrderResDto::fromEntity).toList();
        return new ResponseEntity<>(
                ApiResponse.success(data, "본인 발령 이력 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/by-member/{memberId}")
    public ResponseEntity<?> findByMember(@PathVariable UUID memberId) {
        List<PersonnelOrderResDto> data = repository
                .findByMemberIdOrderByEffectiveDateDescCreatedAtDesc(memberId)
                .stream().map(PersonnelOrderResDto::fromEntity).toList();
        return new ResponseEntity<>(
                ApiResponse.success(data, "직원 발령 이력 조회 성공"),
                HttpStatus.OK);
    }

    @GetMapping("/by-company")
    public ResponseEntity<?> findByCompany(@RequestHeader("X-User-CompanyId") UUID companyId) {
        List<PersonnelOrderResDto> data = repository
                .findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)
                .stream().map(PersonnelOrderResDto::fromEntity).toList();
        return new ResponseEntity<>(
                ApiResponse.success(data, "회사 발령 이력 조회 성공"),
                HttpStatus.OK);
    }
}
