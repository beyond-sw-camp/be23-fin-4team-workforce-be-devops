package com._team._team.goal.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA AttributeConverter: List&lt;Reaction&gt; ↔ JSON 문자열 자동 변환.
 * DB 컬럼은 기존 TEXT(JSON 배열) 그대로 유지하면서,
 * 엔티티에서는 List&lt;Reaction&gt;으로 타입-세이프하게 다룹니다.
 */
@Converter
public class ReactionListConverter implements AttributeConverter<List<Reaction>, String> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Reaction> attribute) {
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
    public List<Reaction> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OM.readValue(dbData, new TypeReference<List<Reaction>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
