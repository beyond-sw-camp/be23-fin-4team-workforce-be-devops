package com._team._team.approval.domain;

import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.RequestType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class ApprovalRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID requestId;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private ApprovalDocument approvalDocument;

    @Column(length = 30, unique = true)
    private String documentNumber; // 공문 번호 (예: 인사-2026-0147). OFFICIAL 타입에서만 사용

    @Column(nullable = false)
    private UUID memberId; // 요청자ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType requestType; // VACATION, ATTENDANCE, HR_MOVEMENT, SALARY, GENERAL,CERTIFICATE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String contentJson; // 실제 입력된 값 (key: value 형태)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus requestStatus;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    @Builder.Default
    @Column(nullable = false)
    private String delYn = "N";

    @Column(columnDefinition = "TEXT")
    private String cancelReason;

    @Builder.Default
    @Column(nullable = false)
    private String sendYn = "N"; // 공문 발송 여부

    @Builder.Default
    @Column(nullable = false)
    private String isDeptVisibleYn = "Y"; // 부서 문서함 공개 여부 (작성자 선택, 건별)

    // === 요청자 스냅샷 (작성 시점 정보) ===
    @Column(nullable = false)
    private String requesterName; // 요청자 이름 (작성 시점)

    @Column
    private UUID requesterOrganizationId; // 요청자 조직 ID (작성 시점, 조직 없는 사용자는 NULL 허용)

    @Column
    private String requesterOrganizationName; // 요청자 조직명 (작성 시점)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String formSchemaSnapshot; // 결재 작성 시점의 양식 스키마 스냅샷


    public void updateStatus(RequestStatus status) {
        this.requestStatus = status;
    }

    public void cancel(String reason) {
        this.requestStatus = RequestStatus.CANCELED;
        this.cancelReason = reason;
    }

    public void updateContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public void updateStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public void updateEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public void updateDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public void send() {
        this.sendYn = "Y";
    }

}
