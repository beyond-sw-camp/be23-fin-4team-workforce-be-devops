package com._team._team.goal.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JPA AttributeConverter: List&lt;UUID&gt; ↔ JSON 문자열 자동 변환.
 * DB 컬럼은 기존 TEXT(JSON 배열) 그대로 유지하면서,
 * 엔티티에서는 List&lt;UUID&gt;로 타입-세이프하게 다룹니다.
 */
@Converter
public class UuidListConverter implements AttributeConverter<List<UUID>, String> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<UUID> attribute) {
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
    public List<UUID> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OM.readValue(dbData, new TypeReference<List<UUID>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
