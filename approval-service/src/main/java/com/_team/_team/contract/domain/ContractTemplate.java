package com._team._team.contract.domain;

import com._team._team.contract.domain.enums.ContractType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class ContractTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID templateId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String formSchema;

    @Builder.Default
    @Column(nullable = false)
    private String isActiveYn = "Y";

    @Builder.Default
    @Column(nullable = false)
    private String delYn = "N";

    public void activate() {
        this.isActiveYn = "Y";
    }

    public void deactivate() {
        this.isActiveYn = "N";
    }

    public void updateFormSchema(String formSchema) {
        this.formSchema = formSchema;
    }

    public void updateTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public void delete() {
        this.delYn = "Y";
    }
}
