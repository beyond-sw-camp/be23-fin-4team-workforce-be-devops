package com._team._team.calendar.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "legal_holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LegalHoliday extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID legalHolidayId;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String holidayName;

    @Column(nullable = false)
    private int year;
}