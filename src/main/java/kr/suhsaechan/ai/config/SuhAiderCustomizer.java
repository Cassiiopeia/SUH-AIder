package kr.suhsaechan.ai.config;

import kr.suhsaechan.ai.model.JsonSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SuhAiderEngine 전역 설정 커스터마이저
 *
 * 사용자가 @Bean으로 등록하여 전역 기본 설정을 제공할 수 있습니다.
 *
 * <p>v2.0에서 {@code customReadTimeout}, {@code promptPrefix}, {@code promptSuffix}를
 * 제거했습니다. 세 필드 모두 엔진이 읽지 않아 설정해도 아무 효과가 없었습니다.
 * 타임아웃은 {@code suh.aider.read-timeout}으로 설정하세요.</p>
 *
 * 사용 예제:
 * <pre>
 * {@code @Configuration}
 * public class AiConfig {
 *
 *     {@code @Bean}
 *     public SuhAiderCustomizer suhAiderCustomizer() {
 *         return SuhAiderCustomizer.builder()
 *             .defaultResponseSchema(JsonSchema.of(
 *                 "result", "string",
 *                 "success", "boolean"
 *             ))
 *             .build();
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuhAiderCustomizer {

    /**
     * 모든 요청에 기본으로 적용할 responseSchema
     *
     * 개별 요청에 responseSchema가 지정되면 개별 요청이 우선합니다.
     * null이면 사용하지 않습니다.
     */
    private JsonSchema defaultResponseSchema;
}
