package kr.suhsaechan.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama Generate API 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {

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
}
