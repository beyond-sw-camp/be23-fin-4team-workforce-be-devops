package com._team._team.attendance.repository;

import com._team._team.attendance.domain.CompanyLeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyLeaveTypeRepository extends JpaRepository<CompanyLeaveType, UUID> {

    // 회사별 활성 휴가 종류 목록 (직원 신청 UI, 관리자 조회 공통)
    List<CompanyLeaveType> findAllByCompanyIdAndDelYnOrderByDisplayOrder(
            UUID companyId, String delYn);

    // 시스템 식별용 (code 기반 내부 조회, 중복 체크, 배치 로직)
    Optional<CompanyLeaveType> findByCompanyIdAndCodeAndDelYn(
            UUID companyId, String code, String delYn);

    /** 소프트 삭제 행 복구용 */
    Optional<CompanyLeaveType> findByCompanyIdAndCode(UUID companyId, String code);

    // 휴가종류 반환
    Optional<CompanyLeaveType> findByCompanyLeaveTypeIdAndCompanyIdAndDelYn(
            UUID companyLeaveTypeId, UUID companyId, String delYn);

    // 회사에 기본휴가 8종이 이미 자동 생성됐는지 확인
    boolean existsByCompanyIdAndIsSystemDefault(UUID companyId, Boolean isSystemDefault);
}
