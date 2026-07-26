package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SUH-AIDER Generate API 요청 DTO
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // 미설정 옵션을 null로 보내지 않는다
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
     * Ollama 구조화 출력 포맷
     * "json" 문자열 또는 JSON Schema 객체를 직접 지정할 때 사용합니다.
     * responseSchema를 쓰면 이 필드가 자동으로 채워집니다.
     */
    private Object format;

    /**
     * 모델 메모리 유지 시간
     * 예: "5m", "1h", "-1" (영구)
     */
    @JsonProperty("keep_alive")
    private String keepAlive;

    /**
     * 추가 모델 옵션 (temperature, top_k, top_p, seed 등)
     */
    private Map<String, Object> options;

    /**
     * JSON 응답 강제를 위한 스키마 정의
     *
     * <p>v2.0부터 이 스키마는 Ollama의 네이티브 {@code format} 파라미터로 전달됩니다.
     * v1.x처럼 프롬프트에 영어 지시문을 덧붙이지 않으므로 프롬프트가 원본 그대로 전송됩니다.
     * 스트리밍({@code generateStream})에서도 동일하게 동작합니다.</p>
     *
     * 사용 예제:
     * <pre>
     * SuhAiderRequest.builder()
     *     .model("gemma4:e2b")
     *     .prompt("Extract name and age")
     *     .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
     *     .build();
     * </pre>
     *
     * @see JsonSchema
     */
    @JsonIgnore  // format으로 변환되어 전송됨 (내부 처리용)
    private JsonSchema responseSchema;
}
