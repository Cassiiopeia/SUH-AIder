package kr.suhsaechan.ai.annotation;

import java.lang.annotation.*;

/**
 * JSON Schema의 클래스 레벨 메타데이터를 정의하는 어노테이션
 *
 * <p>사용 예제:</p>
 * <pre>
 * {@code
 * @AiClass(
 *     title = "사용자 정보",
 *     description = "회원가입 시 입력받는 사용자 기본 정보",
 *     example = "{\"name\":\"홍길동\",\"age\":30}"
 * )
 * public class UserResponse {
 *     // ...
 * }
 * }
 * </pre>
 *
 * @since 0.0.9
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiClass {

    /**
     * 스키마의 제목
     * @return 제목 문자열
     */
    String title() default "";

    /**
     * 스키마에 대한 설명 (LLM이 구조를 이해하는데 도움)
     * @return 설명 문자열
     */
    String description() default "";

    /**
     * JSON 응답 예제 (선택적)
     * @return JSON 형식의 예제 문자열
     */
    String example() default "";
}
