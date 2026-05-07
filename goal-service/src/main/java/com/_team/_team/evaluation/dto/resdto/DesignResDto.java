package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.EvaluationDesign;
import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.converter.GradeConfig;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [D-4] 평가 설계 응답 DTO.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DesignResDto {

    private UUID designId;
    private UUID companyId;
    private String name;

    /** [D-4] 타입 세이프 섹션 목록 — 신규 포맷 */
    private List<EvaluationSection> sections;

    /** [D-4] 타입 세이프 등급 설정 — 신규 포맷 */
    private GradeConfig gradeConfig;

    /** 화면 표시용 버전 라벨 (예: v2) */
    private String designVersion;
    /** 기본 템플릿 여부 */
    private Boolean defaultTemplate;
    /** 최근 수정 시각 */
    private LocalDateTime updatedAt;

    public static DesignResDto from(EvaluationDesign e) {
        int v = e.getTemplateVersion() == null ? 1 : Math.max(1, e.getTemplateVersion());
        List<EvaluationSection> sections = e.getSections() != null ? e.getSections() : new ArrayList<>();

        return DesignResDto.builder()
                .designId(e.getDesignId())
                .companyId(e.getCompanyId())
                .name(e.getName())
                .sections(sections)
                .gradeConfig(e.getGradeConfig())
                .designVersion("v" + v)
                .defaultTemplate(Boolean.TRUE.equals(e.getDefaultTemplate()))
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
