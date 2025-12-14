package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SUH-AIDER Chat API 응답 DTO
 * Ollama /api/chat 응답 형식
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 사용된 모델명
     */
    private String model;

    /**
     * 생성 시간 (ISO 8601)
     */
    @JsonProperty("created_at")
    private String createdAt;

    /**
     * AI 응답 메시지
     */
    private ChatMessage message;

    /**
     * 생성 완료 여부
     */
    private Boolean done;

    /**
     * 종료 이유
     * "stop": 정상 완료
     * "length": 토큰 제한
     * "tool_calls": 도구 호출
     */
    @JsonProperty("done_reason")
    private String doneReason;

    /**
     * 전체 처리 시간 (나노초)
     */
    @JsonProperty("total_duration")
    private Long totalDuration;

    /**
     * 모델 로드 시간 (나노초)
     */
    @JsonProperty("load_duration")
    private Long loadDuration;

    /**
     * 프롬프트 평가 토큰 수
     */
    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    /**
     * 프롬프트 평가 시간 (나노초)
     */
    @JsonProperty("prompt_eval_duration")
    private Long promptEvalDuration;

    /**
     * 응답 생성 토큰 수
     */
    @JsonProperty("eval_count")
    private Integer evalCount;

    /**
     * 응답 생성 시간 (나노초)
     */
    @JsonProperty("eval_duration")
    private Long evalDuration;

    // ========== 편의 메서드 ==========

    /**
     * 응답 텍스트 반환 (간편)
     *
     * @return AI 응답 텍스트 (message가 없으면 null)
     */
    public String getContent() {
        return message != null ? message.getContent() : null;
    }

    /**
     * 처리 시간(ms) 반환
     *
     * @return 밀리초 단위 처리 시간
     */
    public Long getTotalDurationMs() {
        return totalDuration != null ? totalDuration / 1_000_000 : null;
    }

    /**
     * 도구 호출 여부 확인
     *
     * @return 도구 호출이 있으면 true
     */
    public boolean hasToolCalls() {
        return message != null
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }
}
