package kr.suhsaechan.ai.annotation;

import java.lang.annotation.*;

/**
 * JSON Schema에서 특정 필드를 제외하는 마커 어노테이션
 *
 * <p>이 어노테이션이 붙은 필드는 {@code JsonSchema.fromClass()}로 스키마를 생성할 때 무시됩니다.</p>
 *
 * <p>사용 예제:</p>
 * <pre>
 * {@code
 * @AiClass(title = "사용자 정보")
 * public class UserResponse {
 *     @AiSchema(description = "사용자 이름")
 *     private String name;
 *
 *     @AiHidden  // JSON 스키마에서 제외
 *     private String internalId;
 *
 *     @AiHidden  // 보안상 민감한 정보 제외
 *     private String password;
 * }
 * }
 * </pre>
 *
 * @since 0.0.9
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiHidden {
    // 마커 어노테이션 - 속성 없음
}
