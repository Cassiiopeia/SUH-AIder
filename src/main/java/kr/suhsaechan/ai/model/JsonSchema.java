package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import kr.suhsaechan.ai.util.JsonSchemaClassParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * JSON Schema 정의 클래스
 *
 * 사용 예제:
 * <pre>
 * // 방식 1: 간단한 스키마 (1-5개 필드)
 * JsonSchema schema = JsonSchema.of("name", "string", "age", "integer");
 *
 * // 방식 2: 복잡한 스키마 (빌더 패턴)
 * JsonSchema schema = JsonSchema.builder()
 *     .title("사용자 정보")
 *     .description("사용자 기본 정보")
 *     .properties(propertiesMap)
 *     .requiredFields(Arrays.asList("name"))
 *     .build();
 *
 * // 또는 빌더로 생성 후 인스턴스 메서드 사용
 * JsonSchema schema = JsonSchema.builder()
 *     .build()
 *     .property("name", "string")
 *     .property("age", "integer")
 *     .required("name");
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonSchema {

    /**
     * 스키마 타입 (기본: "object")
     */
    @Builder.Default
    private String type = "object";

    /**
     * 스키마 제목 (@AiClass의 title)
     */
    private String title;

    /**
     * 스키마 설명 (@AiClass의 description)
     */
    private String description;

    /**
     * 속성 정의 (필드명 → 타입 정보)
     */
    @Builder.Default
    private Map<String, PropertySchema> properties = new LinkedHashMap<>();

    /**
     * 필수 필드 목록
     */
    @JsonProperty("required")
    @Builder.Default
    private List<String> requiredFields = new ArrayList<>();

    /**
     * 배열 타입의 경우 아이템 스키마
     */
    private JsonSchema items;

    /**
     * ✅ 방식 1: 초간단 스키마 생성 (정적 팩토리 메서드)
     *
     * @param keyValuePairs "필드명", "타입", "필드명", "타입", ... (짝수개)
     * @return JsonSchema 객체
     *
     * 예: JsonSchema.of("name", "string", "age", "integer")
     */
    public static JsonSchema of(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                "키-값 쌍이 짝수개여야 합니다. 예: of(\"name\", \"string\", \"age\", \"integer\")"
            );
        }

        JsonSchema schema = new JsonSchema();
        schema.setType("object");

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String fieldName = keyValuePairs[i];
            String fieldType = keyValuePairs[i + 1];
            schema.properties.put(fieldName, PropertySchema.of(fieldType));
        }

        return schema;
    }


    /**
     * ✅ 방식 3: 클래스 기반 스키마 생성 (리플렉션)
     *
     * @param clazz 스키마를 생성할 클래스 (@AiClass, @AiSchema 어노테이션 사용)
     * @param <T> 클래스 타입
     * @return JsonSchema 객체
     *
     * 예:
     * <pre>
     * {@code
     * @AiClass(title = "사용자 정보")
     * public class UserResponse {
     *     @AiSchema(description = "이름", required = true)
     *     private String name;
     * }
     *
     * JsonSchema schema = JsonSchema.fromClass(UserResponse.class);
     * }
     * </pre>
     *
     * @since 0.0.9
     */
    public static <T> JsonSchema fromClass(Class<T> clazz) {
        return JsonSchemaClassParser.parse(clazz);
    }

    /**
     * 헬퍼 메서드: 중첩 객체 생성
     *
     * @param keyValuePairs "필드명", "타입", ...
     * @return JsonSchema 객체
     *
     * 예: JsonSchema.object("firstName", "string", "lastName", "string")
     */
    public static JsonSchema object(String... keyValuePairs) {
        return of(keyValuePairs);
    }

    /**
     * 헬퍼 메서드: 배열 스키마 생성
     *
     * @param itemType 배열 아이템의 타입 ("string", "integer", "object" 등)
     * @return JsonSchema 객체
     *
     * 예: JsonSchema.array("string") → 문자열 배열
     */
    public static JsonSchema array(String itemType) {
        JsonSchema schema = new JsonSchema();
        schema.setType("array");

        JsonSchema itemSchema = new JsonSchema();
        itemSchema.setType(itemType);
        schema.setItems(itemSchema);

        return schema;
    }

    /**
     * 헬퍼 메서드: 객체 배열 스키마 생성
     *
     * @param itemSchema 배열 아이템의 스키마
     * @return JsonSchema 객체
     *
     * 예: JsonSchema.arrayOf(JsonSchema.object("name", "string"))
     */
    public static JsonSchema arrayOf(JsonSchema itemSchema) {
        JsonSchema schema = new JsonSchema();
        schema.setType("array");
        schema.setItems(itemSchema);
        return schema;
    }

    /**
     * 필수 필드 지정 (체이닝 가능)
     *
     * @param fields 필수 필드명 목록
     * @return 현재 JsonSchema 인스턴스
     */
    public JsonSchema required(String... fields) {
        this.requiredFields.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * 속성 추가 (체이닝 가능)
     *
     * @param name 필드명
     * @param type 타입 ("string", "integer", "boolean" 등)
     * @return 현재 JsonSchema 인스턴스
     */
    public JsonSchema property(String name, String type) {
        this.properties.put(name, PropertySchema.of(type));
        return this;
    }

    /**
     * 중첩 객체 속성 추가 (체이닝 가능)
     *
     * @param name 필드명
     * @param nestedSchema 중첩된 스키마
     * @return 현재 JsonSchema 인스턴스
     */
    public JsonSchema property(String name, JsonSchema nestedSchema) {
        this.properties.put(name, PropertySchema.of(nestedSchema));
        return this;
    }

    /**
     * Chat API format 파라미터용 객체로 변환
     * Ollama Chat API는 format에 JSON Schema 객체를 직접 받습니다.
     *
     * @return format 파라미터에 전달할 Map 객체
     */
    public Map<String, Object> toFormatObject() {
        Map<String, Object> formatMap = new LinkedHashMap<>();
        formatMap.put("type", this.type);

        if (this.title != null) {
            formatMap.put("title", this.title);
        }
        if (this.description != null) {
            formatMap.put("description", this.description);
        }

        if (this.properties != null && !this.properties.isEmpty()) {
            Map<String, Object> propsMap = new LinkedHashMap<>();
            for (Map.Entry<String, PropertySchema> entry : this.properties.entrySet()) {
                propsMap.put(entry.getKey(), entry.getValue().toMap());
            }
            formatMap.put("properties", propsMap);
        }

        if (this.requiredFields != null && !this.requiredFields.isEmpty()) {
            formatMap.put("required", this.requiredFields);
        }

        if (this.items != null) {
            formatMap.put("items", this.items.toFormatObject());
        }

        return formatMap;
    }

}
