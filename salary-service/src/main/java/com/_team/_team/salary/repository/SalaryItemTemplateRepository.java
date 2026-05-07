package com._team._team.salary.repository;

import com._team._team.salary.domain.SalaryItemTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryItemTemplateRepository extends JpaRepository<SalaryItemTemplate, UUID> {

    /** 회사별 삭제되지 않은 급여 항목 템플릿 목록 조회 */
    List<SalaryItemTemplate> findByCompanyIdAndDelYn(UUID companyId, String delYn);

    /** 동일 회사 내 같은 이름의 활성 항목 존재 여부 확인 (중복 검증용) */
    boolean existsByCompanyIdAndItemNameAndDelYn(UUID companyId, String itemName, String delYn);

    // 표준 급여 항목 템플릿 생성용
    Optional<SalaryItemTemplate> findByCompanyIdAndItemNameAndDelYn(
            UUID companyId, String itemName, String delYn);
}
