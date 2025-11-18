package kr.suhsaechan.ai.util;

import kr.suhsaechan.ai.annotation.*;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.PropertySchema;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;

/**
 * Java 클래스로부터 JSON Schema를 자동 생성하는 유틸리티
 *
 * <p>클래스의 필드와 어노테이션(@AiClass, @AiSchema, @AiHidden, @AiArraySchema)을
 * 분석하여 JSON Schema 객체를 생성합니다.</p>
 *
 * @since 0.0.9
 */
@Slf4j
public class JsonSchemaClassParser {

    /**
     * 클래스로부터 JSON Schema 생성
     *
     * @param clazz 파싱할 클래스
     * @return 생성된 JsonSchema
     */
    public static JsonSchema parse(Class<?> clazz) {
        log.debug("클래스 파싱 시작: {}", clazz.getName());
        return parseInternal(clazz, new HashSet<>());
    }

    /**
     * 순환 참조 방지를 위한 내부 파싱 메서드
     *
     * @param clazz 파싱할 클래스
     * @param visitedClasses 이미 방문한 클래스 목록 (순환 참조 방지)
     * @return 생성된 JsonSchema
     */
    private static JsonSchema parseInternal(Class<?> clazz, Set<Class<?>> visitedClasses) {
        // 순환 참조 체크
        if (visitedClasses.contains(clazz)) {
            log.warn("순환 참조 감지: {} (빈 스키마 반환)", clazz.getName());
            return JsonSchema.object();
        }

        visitedClasses.add(clazz);

        JsonSchema.JsonSchemaBuilder builder = JsonSchema.builder();
        Map<String, PropertySchema> properties = new LinkedHashMap<>();
        List<String> requiredFields = new ArrayList<>();

        // 1. @AiClass 어노테이션 처리
        AiClass aiClass = clazz.getAnnotation(AiClass.class);
        if (aiClass != null) {
            if (!aiClass.title().isEmpty()) {
                builder.title(aiClass.title());
            }
            if (!aiClass.description().isEmpty()) {
                builder.description(aiClass.description());
            }
        }

        // 2. 모든 필드 순회
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            // @AiHidden 체크
            if (field.isAnnotationPresent(AiHidden.class)) {
                log.debug("필드 제외 (@AiHidden): {}.{}", clazz.getSimpleName(), field.getName());
                continue;
            }

            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            // PropertySchema 생성
            PropertySchema propSchema = buildPropertySchema(field, fieldType, visitedClasses);

            properties.put(fieldName, propSchema);

            // required 필드 수집
            AiSchema aiSchema = field.getAnnotation(AiSchema.class);
            if (aiSchema != null && aiSchema.required()) {
                requiredFields.add(fieldName);
            }

            log.debug("필드 추가: {}.{} (type: {})", clazz.getSimpleName(), fieldName, propSchema.getType());
        }

        // 빌더에 properties와 requiredFields 설정
        builder.properties(properties);
        if (!requiredFields.isEmpty()) {
            builder.requiredFields(requiredFields);
        }

        visitedClasses.remove(clazz);
        return builder.build();
    }

    /**
     * 필드로부터 PropertySchema 생성
     *
     * @param field 대상 필드
     * @param fieldType 필드 타입
     * @param visitedClasses 순환 참조 방지용
     * @return PropertySchema
     */
    private static PropertySchema buildPropertySchema(Field field, Class<?> fieldType, Set<Class<?>> visitedClasses) {
        PropertySchema schema = new PropertySchema();
        AiSchema aiSchema = field.getAnnotation(AiSchema.class);

        // 1. 타입 결정
        String jsonType = determineJsonType(field, fieldType, aiSchema);
        schema.setType(jsonType);

        // 2. @AiSchema 어노테이션 처리
        if (aiSchema != null) {
            applyAiSchemaAnnotation(schema, aiSchema);
        }

        // 3. 배열/컬렉션 타입 처리
        if ("array".equals(jsonType)) {
            applyArraySchema(schema, field, visitedClasses);
        }

        // 4. 중첩 객체 타입 처리
        if ("object".equals(jsonType) && !isBasicType(fieldType)) {
            JsonSchema nestedSchema = parseInternal(fieldType, visitedClasses);
            schema.setProperties(nestedSchema.getProperties());
        }

        return schema;
    }

    /**
     * JSON 타입 결정 (타입 추론)
     *
     * @param field 필드
     * @param fieldType 필드 타입
     * @param aiSchema @AiSchema 어노테이션 (null 가능)
     * @return JSON 타입 문자열
     */
    private static String determineJsonType(Field field, Class<?> fieldType, AiSchema aiSchema) {
        // @AiSchema의 type 속성 우선
        if (aiSchema != null && !aiSchema.type().isEmpty()) {
            return aiSchema.type();
        }

        // 타입별 자동 추론
        if (fieldType == String.class) {
            return "string";
        } else if (fieldType == Integer.class || fieldType == int.class ||
                fieldType == Long.class || fieldType == long.class ||
                fieldType == Short.class || fieldType == short.class ||
                fieldType == Byte.class || fieldType == byte.class) {
            return "integer";
        } else if (fieldType == Double.class || fieldType == double.class ||
                fieldType == Float.class || fieldType == float.class ||
                fieldType == BigDecimal.class) {
            return "number";
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            return "boolean";
        } else if (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            return "array";
        } else {
            return "object";
        }
    }

    /**
     * @AiSchema 어노테이션 속성을 PropertySchema에 적용
     *
     * @param schema 대상 PropertySchema
     * @param aiSchema @AiSchema 어노테이션
     */
    private static void applyAiSchemaAnnotation(PropertySchema schema, AiSchema aiSchema) {
        // description
        if (!aiSchema.description().isEmpty()) {
            schema.setDescription(aiSchema.description());
        }

        // example
        if (!aiSchema.example().isEmpty()) {
            schema.setExample(aiSchema.example());
        }

        // format
        if (!aiSchema.format().isEmpty()) {
            schema.setFormat(aiSchema.format());
        }

        // pattern
        if (!aiSchema.pattern().isEmpty()) {
            schema.setPattern(aiSchema.pattern());
        }

        // allowableValues (enum)
        if (aiSchema.allowableValues().length > 0) {
            schema.setEnumValues(Arrays.asList(aiSchema.allowableValues()));
        }

        // 숫자 제약
        if (!aiSchema.minimum().isEmpty()) {
            schema.setMinimum(Double.parseDouble(aiSchema.minimum()));
        }
        if (!aiSchema.maximum().isEmpty()) {
            schema.setMaximum(Double.parseDouble(aiSchema.maximum()));
        }
        if (aiSchema.exclusiveMinimum()) {
            schema.setExclusiveMinimum(true);
        }
        if (aiSchema.exclusiveMaximum()) {
            schema.setExclusiveMaximum(true);
        }

        // 문자열 제약
        if (aiSchema.minLength() != Integer.MIN_VALUE) {
            schema.setMinLength(aiSchema.minLength());
        }
        if (aiSchema.maxLength() != Integer.MAX_VALUE) {
            schema.setMaxLength(aiSchema.maxLength());
        }

        // 배열 제약 (기본값)
        if (aiSchema.minItems() != Integer.MIN_VALUE) {
            schema.setMinItems(aiSchema.minItems());
        }
        if (aiSchema.maxItems() != Integer.MAX_VALUE) {
            schema.setMaxItems(aiSchema.maxItems());
        }
    }

    /**
     * @AiArraySchema 어노테이션 및 제네릭 타입으로 배열 스키마 구성
     *
     * @param schema 대상 PropertySchema
     * @param field 필드
     * @param visitedClasses 순환 참조 방지용
     */
    private static void applyArraySchema(PropertySchema schema, Field field, Set<Class<?>> visitedClasses) {
        AiArraySchema arraySchema = field.getAnnotation(AiArraySchema.class);

        // uniqueItems
        if (arraySchema != null && arraySchema.uniqueItems()) {
            schema.setUniqueItems(true);
        }

        // minItems, maxItems (@AiArraySchema 우선)
        if (arraySchema != null) {
            if (arraySchema.minItems() != Integer.MIN_VALUE) {
                schema.setMinItems(arraySchema.minItems());
            }
            if (arraySchema.maxItems() != Integer.MAX_VALUE) {
                schema.setMaxItems(arraySchema.maxItems());
            }
        }

        // 배열 항목 타입 결정
        Class<?> itemType = null;

        // 1. @AiArraySchema의 itemType 우선
        if (arraySchema != null && arraySchema.itemType() != Object.class) {
            itemType = arraySchema.itemType();
        } else {
            // 2. 제네릭 타입 추론 (List<T>, Set<T> 등)
            itemType = extractGenericType(field);
        }

        // 3. 항목 타입 스키마 생성
        if (itemType != null) {
            PropertySchema itemSchema = new PropertySchema();
            String itemJsonType = determineJsonType(field, itemType, null);
            itemSchema.setType(itemJsonType);

            // 중첩 객체인 경우 재귀 파싱
            if ("object".equals(itemJsonType) && !isBasicType(itemType)) {
                JsonSchema nestedSchema = parseInternal(itemType, visitedClasses);
                itemSchema.setProperties(nestedSchema.getProperties());
            }

            schema.setItems(itemSchema);
        }
    }

    /**
     * 제네릭 타입에서 실제 타입 추출 (List<String> → String)
     *
     * @param field 필드
     * @return 제네릭 타입 클래스 (없으면 null)
     */
    private static Class<?> extractGenericType(Field field) {
        Type genericType = field.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericType;
            Type[] typeArgs = paramType.getActualTypeArguments();

            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }

        // 배열 타입 (String[], int[] 등)
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }

        return null;
    }

    /**
     * 기본 타입 여부 확인 (추가 파싱 불필요한 타입)
     *
     * @param clazz 클래스
     * @return 기본 타입이면 true
     */
    private static boolean isBasicType(Class<?> clazz) {
        return clazz == String.class ||
                clazz == Integer.class || clazz == int.class ||
                clazz == Long.class || clazz == long.class ||
                clazz == Double.class || clazz == double.class ||
                clazz == Float.class || clazz == float.class ||
                clazz == Boolean.class || clazz == boolean.class ||
                clazz == Short.class || clazz == short.class ||
                clazz == Byte.class || clazz == byte.class ||
                clazz == BigDecimal.class ||
                clazz.isPrimitive();
    }
}
