package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
     * JSON Schema 표준에 따라 Double 타입 사용 (정수/실수 모두 지원)
     */
    private Double minimum;

    /**
     * 최대값 (number/integer 타입용, 선택적)
     * JSON Schema 표준에 따라 Double 타입 사용 (정수/실수 모두 지원)
     */
    private Double maximum;

    /**
     * 최소값 미포함 여부 (minimum > value, minimum을 포함하지 않음)
     */
    private Boolean exclusiveMinimum;

    /**
     * 최대값 미포함 여부 (value < maximum, maximum을 포함하지 않음)
     */
    private Boolean exclusiveMaximum;

    /**
     * 최소 길이 (string 타입용, 선택적)
     */
    private Integer minLength;

    /**
     * 최대 길이 (string 타입용, 선택적)
     */
    private Integer maxLength;

    /**
     * 값의 예제 (선택적)
     */
    private String example;

    /**
     * 값의 포맷 (예: "date", "email", "uri", "uuid" 등, 선택적)
     */
    private String format;

    /**
     * 값의 정규식 패턴 (선택적)
     */
    private String pattern;

    /**
     * 허용 가능한 값 목록 (Enum 용, 선택적)
     */
    private List<String> enumValues;

    /**
     * 배열 타입의 경우 아이템 스키마 (선택적)
     */
    private PropertySchema items;

    /**
     * 최소 배열 항목 수 (array 타입용, 선택적)
     */
    private Integer minItems;

    /**
     * 최대 배열 항목 수 (array 타입용, 선택적)
     */
    private Integer maxItems;

    /**
     * 배열 항목의 중복 허용 여부 (array 타입용, 선택적)
     */
    private Boolean uniqueItems;

    /**
     * 중첩 객체의 속성 정의 (object 타입인 경우, 선택적)
     * 중첩 객체의 properties를 직접 저장할 때 사용
     */
    private Map<String, PropertySchema> properties;

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
