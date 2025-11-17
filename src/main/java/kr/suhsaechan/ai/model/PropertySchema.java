package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON Schema의 속성(Property) 정의 클래스
 *
 * JsonSchema 내부에서 각 필드의 타입 정보를 표현합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertySchema {

    /**
     * 속성 타입 ("string", "integer", "boolean", "number", "object", "array" 등)
     */
    private String type;

    /**
     * 중첩 객체인 경우 스키마 정의
     */
    private JsonSchema nested;

    /**
     * 속성에 대한 설명 (선택적)
     */
    private String description;

    /**
     * 최소값 (number/integer 타입용, 선택적)
     */
    private Integer minimum;

    /**
     * 최대값 (number/integer 타입용, 선택적)
     */
    private Integer maximum;

    /**
     * 최소 길이 (string 타입용, 선택적)
     */
    private Integer minLength;

    /**
     * 최대 길이 (string 타입용, 선택적)
     */
    private Integer maxLength;

    /**
     * 정적 팩토리 메서드: 타입만으로 간단히 생성
     *
     * @param type 속성 타입
     * @return PropertySchema 인스턴스
     */
    public static PropertySchema of(String type) {
        return PropertySchema.builder()
                .type(type)
                .build();
    }

    /**
     * 정적 팩토리 메서드: 중첩 객체 스키마 생성
     *
     * @param nestedSchema 중첩된 JsonSchema
     * @return PropertySchema 인스턴스
     */
    public static PropertySchema of(JsonSchema nestedSchema) {
        return PropertySchema.builder()
                .type("object")
                .nested(nestedSchema)
                .build();
    }

    /**
     * 정적 팩토리 메서드: 설명 포함 생성
     *
     * @param type 속성 타입
     * @param description 설명
     * @return PropertySchema 인스턴스
     */
    public static PropertySchema of(String type, String description) {
        return PropertySchema.builder()
                .type(type)
                .description(description)
                .build();
    }
}
