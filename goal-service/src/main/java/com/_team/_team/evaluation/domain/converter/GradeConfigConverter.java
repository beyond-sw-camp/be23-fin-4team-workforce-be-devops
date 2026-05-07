package com._team._team.evaluation.domain.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter: GradeConfig ↔ JSON 문자열.
 * [D-4] evaluation_design.grade_config_json 컬럼 그대로 유지.
 */
@Converter
public class GradeConfigConverter implements AttributeConverter<GradeConfig, String> {

    private static final ObjectMapper OM = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(GradeConfig attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OM.writeValueAsString(attribute);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public GradeConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OM.readValue(dbData, GradeConfig.class);
        } catch (Exception e) {
            return null;
        }
    }
}
