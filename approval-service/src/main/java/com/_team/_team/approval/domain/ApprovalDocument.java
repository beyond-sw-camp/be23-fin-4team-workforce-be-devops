package com._team._team.approval.domain;

import com._team._team.approval.domain.enums.RequestType;
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
public class ApprovalDocument extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String documentName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String formSchema;

    @Builder.Default
    @Column(nullable = false)
    private String isActiveYn="Y"; //문서 사용 여부

    @Builder.Default
    @Column(nullable = false)
    private String isCalendarVisibleYn = "N"; // 캘린더 연동 여부

    @Column
    private String calendarDisplayName; // 캘린더 표시명 (예: "연차", "출장")

    @Column
    private String calendarStartField; // contentJson에서 시작일로 쓸 필드 key

    @Column
    private String calendarEndField; // contentJson에서 종료일로 쓸 필드 key (null이면 당일)

    @Column
    private String calendarTitleField; // contentJson에서 title 부가 정보로 쓸 필드 key


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType requestType;

    public void activate(){
        this.isActiveYn="Y";
    }

    public void deactivate(){
        this.isActiveYn="N";
    }

    public void updateFormSchema(String formSchema) {
        this.formSchema = formSchema;
    }

    public void updateCalendarSettings(String isCalendarVisibleYn,
                                       String calendarDisplayName,
                                       String calendarStartField,
                                       String calendarEndField,
                                       String calendarTitleField) {
        this.isCalendarVisibleYn = isCalendarVisibleYn;
        this.calendarDisplayName = calendarDisplayName;
        this.calendarStartField = calendarStartField;
        this.calendarEndField = calendarEndField;
        this.calendarTitleField = calendarTitleField;
    }

}
