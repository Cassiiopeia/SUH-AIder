package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 *     .property("name", "string")
 *     .property("age", "integer")
 *     .required("name")
 *     .build();
 * </pre>
 */
@Data
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
     * ✅ 방식 2: 빌더 패턴 (복잡한 스키마용)
     *
     * @return SchemaBuilder 인스턴스
     *
     * 예:
     * <pre>
     * JsonSchema.builder()
     *     .property("name", "string")
     *     .property("age", "integer")
     *     .required("name")
     *     .build();
     * </pre>
     */
    public static SchemaBuilder builder() {
        return new SchemaBuilder();
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
     * 커스텀 빌더 클래스 (기존 Lombok Builder와 병행 사용)
     */
    public static class SchemaBuilder {
        private String type = "object";
        private Map<String, PropertySchema> properties = new LinkedHashMap<>();
        private List<String> requiredFields = new ArrayList<>();
        private JsonSchema items;

        public SchemaBuilder type(String type) {
            this.type = type;
            return this;
        }

        public SchemaBuilder property(String name, String type) {
            this.properties.put(name, PropertySchema.of(type));
            return this;
        }

        public SchemaBuilder property(String name, JsonSchema nestedSchema) {
            this.properties.put(name, PropertySchema.of(nestedSchema));
            return this;
        }

        public SchemaBuilder required(String... fields) {
            this.requiredFields.addAll(Arrays.asList(fields));
            return this;
        }

        public SchemaBuilder items(JsonSchema itemSchema) {
            this.items = itemSchema;
            return this;
        }

        public JsonSchema build() {
            JsonSchema schema = new JsonSchema();
            schema.setType(this.type);
            schema.setProperties(this.properties);
            schema.setRequiredFields(this.requiredFields);
            schema.setItems(this.items);
            return schema;
        }
    }
}
