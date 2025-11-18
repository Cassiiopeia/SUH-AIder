package kr.suhsaechan.ai.annotation;

import java.lang.annotation.*;

/**
 * JSON Schema의 필드 레벨 메타데이터를 정의하는 어노테이션
 *
 * <p>Swagger의 @Schema와 유사한 방식으로 필드의 제약 조건과 설명을 정의합니다.</p>
 *
 * <p>사용 예제:</p>
 * <pre>
 * {@code
 * @AiSchema(
 *     description = "사용자 이름",
 *     required = true,
 *     minLength = 2,
 *     maxLength = 50,
 *     example = "홍길동"
 * )
 * private String name;
 *
 * @AiSchema(
 *     description = "나이",
 *     minimum = "0",
 *     maximum = "150",
 *     example = "30"
 * )
 * private Integer age;
 *
 * @AiSchema(
 *     description = "회원 등급",
 *     allowableValues = {"BRONZE", "SILVER", "GOLD"}
 * )
 * private String membershipLevel;
 * }
 * </pre>
 *
 * @since 0.0.9
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiSchema {

    /**
     * 필드에 대한 설명 (LLM이 필드의 의미를 이해하는데 도움)
     * @return 설명 문자열
     */
    String description() default "";

    /**
     * 필수 필드 여부
     * @return true면 필수, false면 선택적
     */
    boolean required() default false;

    /**
     * 필드 값의 예제
     * @return 예제 값 문자열
     */
    String example() default "";

    /**
     * 값의 포맷 (예: "date", "email", "uri", "uuid" 등)
     * @return 포맷 문자열
     */
    String format() default "";

    /**
     * 값의 정규식 패턴
     * @return 정규식 문자열
     */
    String pattern() default "";

    /**
     * 허용 가능한 값 목록 (Enum 용)
     * @return 허용 값 배열
     */
    String[] allowableValues() default {};

    // ===== 숫자 타입 제약 =====

    /**
     * 최소값 (숫자 타입에 적용)
     * @return 최소값 문자열 (빈 문자열이면 제약 없음)
     */
    String minimum() default "";

    /**
     * 최대값 (숫자 타입에 적용)
     * @return 최대값 문자열 (빈 문자열이면 제약 없음)
     */
    String maximum() default "";

    /**
     * 최소값 미포함 여부 (minimum > value, minimum을 포함하지 않음)
     * @return true면 minimum 값 미포함
     */
    boolean exclusiveMinimum() default false;

    /**
     * 최대값 미포함 여부 (value < maximum, maximum을 포함하지 않음)
     * @return true면 maximum 값 미포함
     */
    boolean exclusiveMaximum() default false;

    // ===== 문자열 타입 제약 =====

    /**
     * 최소 문자열 길이
     * @return 최소 길이 (Integer.MIN_VALUE면 제약 없음)
     */
    int minLength() default Integer.MIN_VALUE;

    /**
     * 최대 문자열 길이
     * @return 최대 길이 (Integer.MAX_VALUE면 제약 없음)
     */
    int maxLength() default Integer.MAX_VALUE;

    // ===== 배열 타입 제약 =====

    /**
     * 최소 배열 항목 수
     * @return 최소 항목 수 (Integer.MIN_VALUE면 제약 없음)
     */
    int minItems() default Integer.MIN_VALUE;

    /**
     * 최대 배열 항목 수
     * @return 최대 항목 수 (Integer.MAX_VALUE면 제약 없음)
     */
    int maxItems() default Integer.MAX_VALUE;

    // ===== 타입 명시 =====

    /**
     * JSON 타입 명시 (자동 추론이 어려운 경우 사용)
     * @return "string", "integer", "number", "boolean", "object", "array" 중 하나
     */
    String type() default "";
}
