package kr.suhsaechan.ai.annotation;

import java.lang.annotation.*;

/**
 * 배열/리스트 필드에 대한 상세 메타데이터를 정의하는 어노테이션
 *
 * <p>배열 또는 컬렉션 타입 필드에 추가적인 제약 조건을 명시할 때 사용합니다.</p>
 *
 * <p>사용 예제:</p>
 * <pre>
 * {@code
 * @AiSchema(description = "관심사 목록")
 * @AiArraySchema(
 *     itemType = String.class,
 *     minItems = 1,
 *     maxItems = 10,
 *     uniqueItems = true
 * )
 * private List<String> interests;
 *
 * @AiSchema(description = "친구 목록")
 * @AiArraySchema(
 *     itemType = UserResponse.class,  // 중첩 객체
 *     minItems = 0,
 *     maxItems = 100
 * )
 * private List<UserResponse> friends;
 *
 * @AiArraySchema(itemType = Integer.class)
 * private int[] scores;
 * }
 * </pre>
 *
 * @since 0.0.9
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiArraySchema {

    /**
     * 배열 항목의 타입
     *
     * <p>제네릭 타입 정보가 런타임에 소거되므로, 배열 항목의 타입을 명시적으로 지정해야 합니다.</p>
     *
     * @return 배열 항목의 클래스 타입
     */
    Class<?> itemType() default Object.class;

    /**
     * 배열 항목의 중복 허용 여부
     *
     * <p>true로 설정하면 배열의 모든 항목이 고유해야 합니다 (Set과 유사).</p>
     *
     * @return true면 중복 불가, false면 중복 허용
     */
    boolean uniqueItems() default false;

    /**
     * 최소 배열 항목 수
     *
     * @return 최소 항목 수 (Integer.MIN_VALUE면 제약 없음)
     */
    int minItems() default Integer.MIN_VALUE;

    /**
     * 최대 배열 항목 수
     *
     * @return 최대 항목 수 (Integer.MAX_VALUE면 제약 없음)
     */
    int maxItems() default Integer.MAX_VALUE;
}
