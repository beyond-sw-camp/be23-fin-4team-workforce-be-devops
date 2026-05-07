package com._team._team.approval.service;

import com._team._team.approval.domain.ApprovalDocument;
import com._team._team.approval.domain.enums.RequestType;
import com._team._team.approval.dto.reqdto.ApprovalDocumentCreateReqDto;
import com._team._team.approval.dto.reqdto.ApprovalDocumentUpdateReqDto;
import com._team._team.approval.dto.resdto.ApprovalDocumentResDto;
import com._team._team.approval.publisher.RagSyncApprovalEventPublisher;
import com._team._team.approval.repository.ApprovalDocumentRepository;
import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncApprovalEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ApprovalDocumentService {

    private final ApprovalDocumentRepository approvalDocumentRepository;
    private final ObjectMapper objectMapper;
    private final RagSyncApprovalEventPublisher ragSyncApprovalEventPublisher;

    @Autowired
    public ApprovalDocumentService(ApprovalDocumentRepository approvalDocumentRepository,
                                   ObjectMapper objectMapper,
                                   RagSyncApprovalEventPublisher ragSyncApprovalEventPublisher) {
        this.approvalDocumentRepository = approvalDocumentRepository;
        this.objectMapper = objectMapper;
        this.ragSyncApprovalEventPublisher = ragSyncApprovalEventPublisher;
    }

    public void initDefaultDocuments(UUID companyId) {
        if (!approvalDocumentRepository.findByCompanyIdAndIsActiveYn(companyId, "Y").isEmpty()) {
            return;
        }

        // ========== VACATION ==========
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("휴가신청서")
                .requestType(RequestType.VACATION)
                .isCalendarVisibleYn("Y")
                .calendarDisplayName("휴가")
                .calendarStartField("startDate")
                .calendarEndField("endDate")
                .calendarTitleField("title")
                .formSchema("""
                {
                  "fields": [
                    { "name": "title", "label": "제목", "type": "text", "required": true },
                    {
                      "name": "vacationType",
                      "label": "휴가 종류",
                      "type": "select",
                      "source": "companyLeaveType",
                      "required": true
                    },
                    { "name": "startDate",  "label": "시작일",   "type": "date",     "required": true },
                    { "name": "endDate",    "label": "종료일",   "type": "date",     "required": true },
                    { "name": "reason",     "label": "휴가 사유","type": "textarea"                   },
                    { "name": "leaveRequestId", "label": "salary 연결 ID", "type": "hidden"           }
                  ]
                }
            """)
                .build());

        // ========== ATTENDANCE ==========
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("연장근무신청")
                .requestType(RequestType.ATTENDANCE)
                .isCalendarVisibleYn("Y")
                .calendarDisplayName("연장근무")
                .calendarStartField("workDate")
                .calendarEndField("workDate")
                .calendarTitleField("title")
                .formSchema("""
                {
                  "fields": [
                    { "name": "title", "label": "제목", "type": "text", "required": true },
                    { "name": "workDate",      "label": "근무일자",  "type": "date",     "required": true },
                    { "name": "startTime",     "label": "시작시간",  "type": "time",     "required": true },
                    { "name": "endTime",       "label": "종료시간",  "type": "time",     "required": true },
                    { "name": "requestReason", "label": "신청 사유", "type": "textarea"                  },
                    { "name": "overtimeRequestId", "label": "salary 연결 ID", "type": "hidden"          }
                  ]
                }
            """)
                .build());

        // 근태 정정 신청
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("근태정정신청")
                .requestType(RequestType.ATTENDANCE)
                .isCalendarVisibleYn("N")
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",             "label": "제목",         "type": "text",     "required": true },
                    { "name": "attendanceDate",    "label": "정정 일자",    "type": "date",     "required": true },
                    { "name": "requestedClockIn",  "label": "정정 출근시각","type": "time"                       },
                    { "name": "requestedClockOut", "label": "정정 퇴근시각","type": "time"                       },
                    { "name": "reason",            "label": "정정 사유",    "type": "textarea", "required": true }
                  ]
                }
            """)
                .build());

        // 조퇴계 - 7일 이내 일자, 조퇴 시각, 사유. 승인 시 정규 8h 유지
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("조퇴계")
                .requestType(RequestType.ATTENDANCE)
                .isCalendarVisibleYn("N")
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",          "label": "제목",         "type": "text",     "required": true },
                    { "name": "attendanceDate", "label": "조퇴 일자",    "type": "date",     "required": true },
                    { "name": "earlyLeaveTime", "label": "조퇴 시각",    "type": "time",     "required": true },
                    { "name": "reason",         "label": "조퇴 사유",    "type": "textarea", "required": true }
                  ]
                }
            """)
                .build());

        // ========== HR ==========

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("휴직 신청서")
                .requestType(RequestType.VACATION)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title", "label": "제목", "type": "text", "required": true },
                    { "name": "type", "label": "휴직 종류", "type": "select", "required": true, "options": ["출산휴가", "육아휴직", "장기병가", "무급휴직", "학업휴직", "군복무"] },
                    { "name": "startDate", "label": "시작일", "type": "date", "required": true },
                    { "name": "endDate", "label": "종료일", "type": "date", "required": true },
                    { "name": "reason", "label": "휴직 사유", "type": "textarea" },
                    { "name": "evidenceFileUrl", "label": "증빙 서류", "type": "file" },
                    { "name": "leaveOfAbsenceId", "label": "salary 연결 ID", "type": "hidden" }
                  ]
                }
                """)
                .build());

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("사직서")
                .requestType(RequestType.HR)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",       "label": "제목",       "type": "text",  "required": true },
                    { "name": "resignDate",  "label": "퇴직 희망일","type": "date",  "required": true },
                    {
                      "name": "resignReason",
                      "label": "사직 사유",
                      "type": "select",
                      "required": true,
                      "options": ["일신상의 사유","건강상의 사유","가족돌봄","이직","기타"]
                    },
                    { "name": "detail", "label": "상세 사유", "type": "textarea" },
                    { "name": "note",   "label": "비고",      "type": "textarea" }
                  ]
                }
            """)
                .build());

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("수당 변경 신청")
                .requestType(RequestType.HR)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title", "label": "제목", "type": "text", "required": true },
                    {
                      "name": "salaryItemTemplateId",
                      "label": "수당 항목",
                      "type": "select",
                      "source": "salaryItemTemplate",
                      "required": true
                    },
                    { "name": "amount",         "label": "신청 금액", "type": "number",   "required": true },
                    { "name": "effectiveFrom",  "label": "적용 시작일","type": "date",    "required": true },
                    { "name": "reason",         "label": "신청 사유", "type": "textarea"                   },
                    { "name": "memberAllowanceId", "label": "salary 연결 ID", "type": "hidden"            }
                  ]
                }
            """)
                .build());

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("출퇴근시간 변경 신청서")
                .requestType(RequestType.ATTENDANCE)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",           "label": "제목",          "type": "text",     "required": true },
                    { "name": "targetYearMonth", "label": "대상 연월",      "type": "text",     "required": true, "placeholder": "YYYY-MM" },
                    {
                      "name": "slotId",
                      "label": "시차출퇴근 스케줄",
                      "type": "select",
                      "source": "flexibleTimeSlot",
                      "required": true
                    },
                    { "name": "requestReason",   "label": "신청 사유",      "type": "textarea"                   },
                    { "name": "selectionId",     "label": "salary 연결 ID", "type": "hidden"                    }
                  ]
                }
            """)
                .build());

        // ========== BUSINESS_TRIP ==========
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("국내출장신청")
                .requestType(RequestType.BUSINESS_TRIP)
                .isCalendarVisibleYn("Y")
                .calendarDisplayName("국내출장")
                .calendarStartField("tripStartDate")
                .calendarEndField("tripEndDate")
                .calendarTitleField(null)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",            "label": "제목",                   "type": "text",     "required": true },
                    { "name": "tripStartDate",    "label": "출장 시작일",             "type": "date",     "required": true },
                    { "name": "tripEndDate",      "label": "출장 종료일",             "type": "date",     "required": true },
                    { "name": "destination",      "label": "출장지",                  "type": "text",     "required": true },
                    { "name": "transportation",   "label": "교통편",                  "type": "text",     "required": true },
                    { "name": "purpose",          "label": "출장목적",                "type": "textarea", "required": true },
                    { "name": "travelers",        "label": "출장자(성명/직급/소속)",   "type": "textarea", "required": true },
                    { "name": "accommodationFee", "label": "숙박비",                  "type": "number"                    },
                    { "name": "mealFee",          "label": "식비",                    "type": "number"                    },
                    { "name": "note",             "label": "비고",                    "type": "textarea"                  }
                  ]
                }
            """)
                .build());

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("해외출장신청")
                .requestType(RequestType.BUSINESS_TRIP)
                .isCalendarVisibleYn("Y")
                .calendarDisplayName("해외출장")
                .calendarStartField("tripStartDate")
                .calendarEndField("tripEndDate")
                .calendarTitleField(null)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",                   "label": "제목",                       "type": "text",     "required": true },
                    { "name": "tripStartDate",           "label": "출장 시작일",                 "type": "date",     "required": true },
                    { "name": "tripEndDate",             "label": "출장 종료일",                 "type": "date",     "required": true },
                    { "name": "destinationCountry",      "label": "출장국가",                    "type": "text",     "required": true },
                    { "name": "destination",             "label": "출장지",                      "type": "text",     "required": true },
                    { "name": "transportation",          "label": "교통편",                      "type": "text",     "required": true },
                    { "name": "purpose",                 "label": "출장목적",                    "type": "textarea", "required": true },
                    { "name": "travelers",               "label": "출장자(사번/성명/직급/소속)",  "type": "textarea", "required": true },
                    { "name": "foreignAccommodationFee", "label": "외화 숙박비",                 "type": "number"                    },
                    { "name": "foreignDailyFee",         "label": "외화 일당",                   "type": "number"                    },
                    { "name": "exchangeRate",            "label": "환율",                        "type": "number"                    },
                    { "name": "krwAccommodationFee",     "label": "원화 숙박비",                 "type": "number"                    },
                    { "name": "krwDailyFee",             "label": "원화 일당",                   "type": "number"                    },
                    { "name": "cashFee",                 "label": "현금",                        "type": "number"                    },
                    { "name": "note",                    "label": "비고",                        "type": "textarea"                  }
                  ]
                }
            """)
                .build());

        // ========== GENERAL ==========
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("업무기안")
                .requestType(RequestType.GENERAL)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",             "label": "제목",     "type": "text",     "required": true },
                    { "name": "effectiveDate",     "label": "시행일자", "type": "date",     "required": true },
                    { "name": "relatedDepartment", "label": "협조부서", "type": "text"                      },
                    { "name": "content",           "label": "내용",     "type": "textarea", "required": true }
                  ]
                }
            """)
                .build());

        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("업무보고서")
                .requestType(RequestType.GENERAL)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",      "label": "제목",      "type": "text",     "required": true },
                    { "name": "reportDate", "label": "보고일",    "type": "date",     "required": true },
                    { "name": "content",    "label": "보고 내용", "type": "textarea", "required": true }
                  ]
                }
            """)
                .build());

        // ========== OFFICIAL ==========
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("공문")
                .requestType(RequestType.OFFICIAL)
                .formSchema("""
                {
                  "fields": [
                    { "name": "title",   "label": "제목", "type": "text",     "required": true },
                    { "name": "content", "label": "본문", "type": "textarea", "required": true },
                    { "name": "note",    "label": "비고", "type": "textarea"                   }
                  ]
                }
            """)
                .build());

        // 인사발령품의서 (조직 개편 시뮬 -> 결재 cascade 용 단일 양식)
        savePersonnelOrderDocument(companyId);

        // RAG 동기화 이벤트 발행 (BULK)
        ragSyncApprovalEventPublisher.publish(
                RagSyncApprovalEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("BULK")
                        .resourceType("APPROVAL_DOCUMENT")
                        .resourceId(null)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    /**
     * "인사발령품의서"
     * 승인 다 끝난후 [발송] 버튼 눌러 공문으로 전 부서 부서문서함에 노출
     */
    private void savePersonnelOrderDocument(UUID companyId) {
        if (approvalDocumentRepository.existsByCompanyIdAndDocumentName(companyId, "인사발령품의서")) {
            return;
        }
        approvalDocumentRepository.save(ApprovalDocument.builder()
                .companyId(companyId)
                .documentName("인사발령품의서")
                .requestType(RequestType.OFFICIAL)
                .formSchema("""
                {
                  "fields": [
                    { "name": "effectiveDate",       "label": "효력 시작일", "type": "date",     "required": true },
                    { "name": "orderCategoryLabel",  "label": "발령 종류",   "type": "text"                       },
                    { "name": "reason",              "label": "발령 사유",   "type": "textarea"                   },
                    { "name": "contentJsonText",     "label": "발령 내역",   "type": "personnel_order_items"      }
                  ]
                }
                """)
                .build());
    }


    // 결재 양식 생성
    public ApprovalDocumentResDto create(UUID companyId, ApprovalDocumentCreateReqDto reqDto) {

        // 같은 회사 내 이름 중복 체크
        if (approvalDocumentRepository.existsByCompanyIdAndDocumentName(companyId, reqDto.getDocumentName())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이미 동일한 이름의 결재 양식이 존재합니다: " + reqDto.getDocumentName());
        }

        ApprovalDocument document = reqDto.toEntity(companyId);

        // 캘린더 연동 검증
        validateCalendarFields(reqDto.getIsCalendarVisibleYn(),
                reqDto.getCalendarDisplayName(), reqDto.getCalendarStartField());

        ApprovalDocument saved = approvalDocumentRepository.save(document);

        // RAG 동기화 이벤트 발행
        ragSyncApprovalEventPublisher.publish(
                RagSyncApprovalEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("APPROVAL_DOCUMENT")
                        .resourceId(saved.getDocumentId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return ApprovalDocumentResDto.fromEntity(saved);
    }

    public ApprovalDocumentResDto update(UUID companyId, UUID documentId,
                                         ApprovalDocumentUpdateReqDto reqDto) {

        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // locked 필드 검증
        validateLockedFields(document.getFormSchema(), reqDto.getFormSchema());

        // formSchema 업데이트
        document.updateFormSchema(reqDto.getFormSchema());

        // 캘린더 설정 업데이트
        if (reqDto.getIsCalendarVisibleYn() != null) {
            validateCalendarFields(reqDto.getIsCalendarVisibleYn(),
                    reqDto.getCalendarDisplayName(), reqDto.getCalendarStartField());
            document.updateCalendarSettings(
                    reqDto.getIsCalendarVisibleYn(),
                    reqDto.getCalendarDisplayName(),
                    reqDto.getCalendarStartField(),
                    reqDto.getCalendarEndField(),
                    reqDto.getCalendarTitleField());
        }

        // RAG 동기화 이벤트 발행
        ragSyncApprovalEventPublisher.publish(
                RagSyncApprovalEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("APPROVAL_DOCUMENT")
                        .resourceId(documentId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return ApprovalDocumentResDto.fromEntity(document);
    }

    private void validateCalendarFields(String isCalendarVisibleYn,
                                        String calendarDisplayName,
                                        String calendarStartField) {
        if ("Y".equals(isCalendarVisibleYn)) {
            if (calendarDisplayName == null || calendarDisplayName.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "캘린더 연동 시 캘린더 표시명은 필수입니다.");
            }
            if (calendarStartField == null || calendarStartField.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "캘린더 연동 시 시작일 필드는 필수입니다.");
            }
        }
    }

    /**
     * locked 필드 검증
     * 기존 formSchema에서 locked:true 인 필드가
     * 새 formSchema에서 빠지거나, name/label/type/순서가 변경되었는지 확인
     */
    private void validateLockedFields(String oldSchemaJson, String newSchemaJson) {
        try {
            Map<String, Object> oldSchema = objectMapper.readValue(
                    oldSchemaJson, new TypeReference<>() {});
            Map<String, Object> newSchema = objectMapper.readValue(
                    newSchemaJson, new TypeReference<>() {});

            List<Map<String, Object>> oldFields = (List<Map<String, Object>>) oldSchema.get("fields");
            List<Map<String, Object>> newFields = (List<Map<String, Object>>) newSchema.get("fields");

            if (oldFields == null) return;

            // locked 필드만 추출
            List<Map<String, Object>> lockedFields = new ArrayList<>();
            for (Map<String, Object> field : oldFields) {
                Boolean locked = (Boolean) field.getOrDefault("locked", false);
                if (Boolean.TRUE.equals(locked)) {
                    lockedFields.add(field);
                }
            }

            if (lockedFields.isEmpty()) return;

            if (newFields == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "필수 항목이 포함된 formSchema가 필요합니다.");
            }

            // 새 formSchema에서 locked 필드 순서·내용 검증
            int newIndex = 0;
            for (Map<String, Object> locked : lockedFields) {
                String lockedName = (String) locked.get("name");
                String lockedLabel = (String) locked.get("label");
                String lockedType = (String) locked.get("type");

                boolean found = false;
                for (int ni = newIndex; ni < newFields.size(); ni++) {
                    Map<String, Object> newField = newFields.get(ni);
                    if (lockedName.equals(newField.get("name"))) {
                        // label 변경 검증
                        if (!lockedLabel.equals(newField.get("label"))) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST,
                                    "필수 항목 '" + lockedLabel + "'의 이름(label)은 변경할 수 없습니다.");
                        }
                        // type 변경 검증
                        if (!lockedType.equals(newField.get("type"))) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST,
                                    "필수 항목 '" + lockedLabel + "'의 타입(type)은 변경할 수 없습니다.");
                        }
                        // locked 플래그 유지 검증
                        Boolean newLocked = (Boolean) newField.getOrDefault("locked", false);
                        if (!Boolean.TRUE.equals(newLocked)) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST,
                                    "필수 항목 '" + lockedLabel + "'의 잠금 상태는 해제할 수 없습니다.");
                        }
                        // 순서 검증: locked 필드끼리의 상대 순서 유지
                        if (ni < newIndex) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST,
                                    "필수 항목의 순서는 변경할 수 없습니다.");
                        }
                        newIndex = ni + 1;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "필수 항목 '" + lockedLabel + "'은(는) 삭제할 수 없습니다.");
                }
            }

        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "formSchema JSON 형식이 올바르지 않습니다.");
        }
    }


    // 결재 양식 단건 조회
    @Transactional(readOnly = true)
    public ApprovalDocumentResDto findById(UUID companyId, UUID documentId) {
        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return ApprovalDocumentResDto.fromEntity(document);
    }

    /**
     * 회사별 활성 양식 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ApprovalDocumentResDto> findActiveByCompanyId(UUID companyId) {
        return approvalDocumentRepository
                .findByCompanyIdAndIsActiveYn(companyId, "Y")
                .stream()
                .map(ApprovalDocumentResDto::fromEntity)
                .toList();
    }

    // 회사별 전체 양식 목록 조회 (관리자용)
    @Transactional(readOnly = true)
    public List<ApprovalDocumentResDto> findAllByCompanyId(UUID companyId) {
        return approvalDocumentRepository
                .findByCompanyId(companyId)
                .stream()
                .map(ApprovalDocumentResDto::fromEntity)
                .toList();
    }

    // 결재 양식 활성화
    public ApprovalDocumentResDto activate(UUID companyId, UUID documentId) {
        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        document.activate();

        // RAG 동기화 이벤트 발행 (활성화는 RAG에 추가)
        ragSyncApprovalEventPublisher.publish(
                RagSyncApprovalEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("APPROVAL_DOCUMENT")
                        .resourceId(documentId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return ApprovalDocumentResDto.fromEntity(document);
    }

    // 결재 양식 비활성화
    public ApprovalDocumentResDto deactivate(UUID companyId, UUID documentId) {
        ApprovalDocument document = approvalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 양식을 찾을 수 없습니다."));

        if (!document.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        document.deactivate();

        // RAG 동기화 이벤트 발행 (비활성화는 RAG에서 삭제)
        ragSyncApprovalEventPublisher.publish(
                RagSyncApprovalEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("APPROVAL_DOCUMENT")
                        .resourceId(documentId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return ApprovalDocumentResDto.fromEntity(document);
    }
}