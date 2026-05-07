package com._team._team.member.dto.resdto;

import com._team._team.member.domain.EmploymentJobHistory;
import com._team._team.member.domain.enums.ChangeType;
import com._team._team.member.domain.enums.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploymentJobHistoryResDto {

    private UUID historyId;
    private UUID memberId;
    private String memberName;
    private String jobGradeName;
    private String organizationName;
    private String changerName;      // 변경자 이름
    private EmploymentType employmentType;
    private ChangeType changeType;
    private String changeReason;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDate promotionDate;
    private LocalDateTime changedAt;

    public static EmploymentJobHistoryResDto fromEntity(
            EmploymentJobHistory history, String changerName) {
        return EmploymentJobHistoryResDto.builder()
                .historyId(history.getHistoryId())
                .memberId(history.getMember().getMemberId())
                .memberName(history.getMember().getName())
                .jobGradeName(history.getJobGrade().getName())
                .organizationName(history.getOrganization().getName())
                .changerName(changerName)
                .employmentType(history.getEmploymentType())
                .changeType(history.getChangeType())
                .changeReason(history.getChangeReason())
                .effectiveFrom(history.getEffectiveFrom())
                .effectiveTo(history.getEffectiveTo())
                .promotionDate(history.getPromotionDate())
                .changedAt(history.getChangedAt())
                .build();
    }
}