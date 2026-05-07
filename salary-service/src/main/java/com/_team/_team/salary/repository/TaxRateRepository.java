package com._team._team.salary.repository;

import com._team._team.salary.domain.TaxRate;
import com._team._team.salary.domain.enums.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    /** 연도별 세율 목록 조회 */
    List<TaxRate> findByApplyYear(Integer applyYear);

    /** 연도 + 세금 유형으로 조회 */
    List<TaxRate> findByApplyYearAndTaxType(Integer applyYear, TaxType taxType);

    /** 중복 체크 : 같은 연도, 같은 유형의 세율 존재 여부 */
    boolean existsByApplyYearAndTaxType(Integer applyYear, TaxType taxType);
}
