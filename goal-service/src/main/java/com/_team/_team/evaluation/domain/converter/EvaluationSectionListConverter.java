package com._team._team.evaluation.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA AttributeConverter: List&lt;EvaluationSection&gt; ↔ JSON 문자열.
 * [D-4] evaluation_design.sections_json 컬럼 그대로 유지하면서 타입 세이프 리스트로 사용.
 */
@Converter
public class EvaluationSectionListConverter implements AttributeConverter<List<EvaluationSection>, String> {

    private static final ObjectMapper OM = new ObjectMapper()
            // 프론트가 새 필드(type, sectionId 등)를 아직 안 보내도 파싱 실패하지 않도록
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(List<EvaluationSection> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OM.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<EvaluationSection> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OM.readValue(dbData, new TypeReference<List<EvaluationSection>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
