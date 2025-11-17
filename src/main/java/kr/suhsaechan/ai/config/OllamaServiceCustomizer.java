package kr.suhsaechan.ai.config;

import kr.suhsaechan.ai.model.JsonSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OllamaService 전역 설정 커스터마이저
 *
 * 사용자가 @Bean으로 등록하여 전역 기본 설정을 제공할 수 있습니다.
 *
 * 사용 예제:
 * <pre>
 * {@code @Configuration}
 * public class AiConfig {
 *
 *     {@code @Bean}
 *     public OllamaServiceCustomizer ollamaCustomizer() {
 *         return OllamaServiceCustomizer.builder()
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
public class OllamaServiceCustomizer {

    /**
     * 모든 요청에 기본으로 적용할 responseSchema
     *
     * 개별 요청에 responseSchema가 지정되면 개별 요청이 우선합니다.
     * null이면 사용하지 않습니다.
     */
    private JsonSchema defaultResponseSchema;

    /**
     * 커스텀 타임아웃 설정 (초 단위, 선택적)
     *
     * null이면 OllamaProperties의 기본값 사용
     */
    private Integer customReadTimeout;

    /**
     * 프롬프트 접두사 (선택적)
     *
     * 모든 프롬프트 앞에 자동으로 추가할 텍스트
     * null이면 사용하지 않습니다.
     */
    private String promptPrefix;

    /**
     * 프롬프트 접미사 (선택적)
     *
     * 모든 프롬프트 뒤에 자동으로 추가할 텍스트
     * null이면 사용하지 않습니다.
     */
    private String promptSuffix;
}
