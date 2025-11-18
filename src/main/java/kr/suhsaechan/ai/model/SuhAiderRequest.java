package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SUH-AIDER Generate API 요청 DTO
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SuhAiderRequest {

    /**
     * AI 모델명 (예: llama2, mistral, codellama)
     */
    private String model;

    /**
     * AI에게 전달할 프롬프트 텍스트
     */
    private String prompt;

    /**
     * 스트림 모드 사용 여부
     * 기본값: false (전체 응답을 한 번에 받음)
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * JSON 응답 강제를 위한 스키마 정의
     * 이 필드가 설정되면 프롬프트에 JSON 형식 지시문이 자동으로 추가됩니다.
     *
     * 사용 예제:
     * <pre>
     * SuhAiderRequest.builder()
     *     .model("gemma3:4b")
     *     .prompt("Extract name and age")
     *     .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
     *     .build();
     * </pre>
     *
     * @see JsonSchema
     */
    @JsonIgnore  // Ollama API로 전송하지 않음 (내부 처리용)
    private JsonSchema responseSchema;
}
