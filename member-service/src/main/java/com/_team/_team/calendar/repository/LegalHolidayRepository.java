package com._team._team.calendar.repository;

import com._team._team.calendar.domain.LegalHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegalHolidayRepository extends JpaRepository<LegalHoliday, UUID> {

    List<LegalHoliday> findByYear(int year);

    boolean existsByYear(int year);

    void deleteByYear(int year);
}
