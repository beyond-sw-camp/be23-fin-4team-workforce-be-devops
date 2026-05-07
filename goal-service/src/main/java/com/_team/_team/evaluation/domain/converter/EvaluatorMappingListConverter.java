package com._team._team.evaluation.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA AttributeConverter: List&lt;EvaluatorMapping&gt; ↔ JSON 문자열 변환.
 * DB 컬럼(evaluator_maps_json) 은 기존 JSON 그대로 유지하고
 * 엔티티 측에서만 타입-세이프 리스트로 다룬다.
 */
@Converter
public class EvaluatorMappingListConverter implements AttributeConverter<List<EvaluatorMapping>, String> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<EvaluatorMapping> attribute) {
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
    public List<EvaluatorMapping> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OM.readValue(dbData, new TypeReference<List<EvaluatorMapping>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
