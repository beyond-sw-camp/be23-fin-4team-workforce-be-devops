package com._team._team.esg.repository;

import com._team._team.company.domain.Company;
import com._team._team.esg.domain.EsgActivitySubject;
import com._team._team.esg.domain.enums.EsgCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgActivitySubjectRepository extends JpaRepository<EsgActivitySubject, UUID> {

    List<EsgActivitySubject> findByCompanyAndDelYn(Company company, String delYn);

    List<EsgActivitySubject> findByCompanyAndCategoryAndDelYn(Company company, EsgCategory category, String delYn);

    Optional<EsgActivitySubject> findByEsgActivitySubjectIdAndDelYn(UUID esgActivitySubjectId, String delYn);
}