package com._team._team.personnel.repository;

import com._team._team.personnel.domain.PersonnelOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PersonnelOrderRepository extends JpaRepository<PersonnelOrder, UUID> {

    /** 직원 본인 발령 이력 - 시간 역순 (최신부터) */
    List<PersonnelOrder> findByMemberIdOrderByEffectiveDateDescCreatedAtDesc(UUID memberId);

    /** 회사 발령 이력 - 시간 역순 */
    List<PersonnelOrder> findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(UUID companyId);

    /** 효력일 도래 + 미적용 - 배치 처리 대상 */
    List<PersonnelOrder> findByAppliedYnAndEffectiveDateLessThanEqual(String appliedYn, LocalDate effectiveDate);
}
