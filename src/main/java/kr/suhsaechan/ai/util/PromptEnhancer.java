package kr.suhsaechan.ai.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.PropertySchema;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 응답 강제를 위한 프롬프트 자동 증강 유틸리티
 *
 * responseSchema가 지정된 경우 프롬프트에 JSON 형식 지시문을 자동으로 추가합니다.
 */
@Slf4j
public class PromptEnhancer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * JSON 응답 강제 프롬프트 생성
     *
     * @param originalPrompt 원본 프롬프트
     * @param schema JSON 스키마 (null이면 원본 그대로 반환)
     * @return 증강된 프롬프트
     */
    public static String enhance(String originalPrompt, JsonSchema schema) {
        if (schema == null) {
            log.debug("스키마가 없으므로 원본 프롬프트 사용");
            return originalPrompt;
        }

        StringBuilder enhanced = new StringBuilder();

        // 시스템 지시문 (영어로 명확하게)
        enhanced.append("IMPORTANT INSTRUCTIONS:\n");
        enhanced.append("- You MUST respond ONLY in valid JSON format\n");
        enhanced.append("- Do NOT include any explanations, markdown code blocks (```), or extra text\n");
        enhanced.append("- Output ONLY the raw JSON object that matches the required structure\n");
        enhanced.append("- All field names must exactly match the specification\n\n");

        // 스키마 정보 추가
        enhanced.append("REQUIRED JSON STRUCTURE:\n");
        String schemaDescription = schemaToReadableString(schema);
        enhanced.append(schemaDescription);
        enhanced.append("\n\n");

        // 원본 프롬프트
        enhanced.append("USER TASK:\n");
        enhanced.append(originalPrompt);

        log.debug("프롬프트 증강 완료 - 원본 {}자 → 증강 {}자",
                originalPrompt.length(), enhanced.length());

        return enhanced.toString();
    }

    /**
     * JsonSchema를 읽기 쉬운 문자열로 변환
     *
     * @param schema JSON 스키마
     * @return 가독성 높은 스키마 설명
     */
    private static String schemaToReadableString(JsonSchema schema) {
        try {
            Map<String, Object> schemaMap = new LinkedHashMap<>();

            // 타입 정보
            schemaMap.put("type", schema.getType());

            // 속성 정보
            if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                Map<String, String> propsMap = new LinkedHashMap<>();

                for (Map.Entry<String, PropertySchema> entry : schema.getProperties().entrySet()) {
                    String fieldName = entry.getKey();
                    PropertySchema propSchema = entry.getValue();

                    if (propSchema.getNested() != null) {
                        // 중첩 객체는 "object {...}" 표시
                        propsMap.put(fieldName, "object (nested)");
                    } else {
                        propsMap.put(fieldName, propSchema.getType());
                    }
                }

                schemaMap.put("properties", propsMap);
            }

            // 필수 필드 정보
            if (schema.getRequiredFields() != null && !schema.getRequiredFields().isEmpty()) {
                schemaMap.put("required", schema.getRequiredFields());
            }

            // 배열인 경우 아이템 정보
            if ("array".equals(schema.getType()) && schema.getItems() != null) {
                schemaMap.put("items", Map.of("type", schema.getItems().getType()));
            }

            // Pretty JSON 출력
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(schemaMap);

        } catch (Exception e) {
            log.warn("스키마 문자열 변환 실패, 기본 포맷 사용: {}", e.getMessage());
            return schema.toString();
        }
    }

    /**
     * 간단한 예제 JSON 생성 (AI에게 참고용)
     *
     * @param schema JSON 스키마
     * @return 예제 JSON 문자열
     */
    public static String generateExampleJson(JsonSchema schema) {
        if (schema == null) {
            return "{}";
        }

        try {
            Map<String, Object> example = new LinkedHashMap<>();

            if (schema.getProperties() != null) {
                for (Map.Entry<String, PropertySchema> entry : schema.getProperties().entrySet()) {
                    String fieldName = entry.getKey();
                    PropertySchema propSchema = entry.getValue();

                    // 타입별 예제 값 생성
                    Object exampleValue = switch (propSchema.getType()) {
                        case "string" -> "example_string";
                        case "integer" -> 0;
                        case "number" -> 0.0;
                        case "boolean" -> true;
                        case "array" -> new Object[]{};
                        case "object" -> propSchema.getNested() != null
                                ? generateExampleJson(propSchema.getNested())
                                : new LinkedHashMap<>();
                        default -> null;
                    };

                    example.put(fieldName, exampleValue);
                }
            }

            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(example);

        } catch (Exception e) {
            log.warn("예제 JSON 생성 실패: {}", e.getMessage());
            return "{}";
        }
    }
}
