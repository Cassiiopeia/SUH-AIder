package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SUH-AIDER Chat API 요청 DTO
 * Ollama /api/chat 엔드포인트 사용
 *
 * <p>사용 예제:</p>
 * <pre>
 * // 단일 메시지
 * ChatRequest request = ChatRequest.builder()
 *     .model("llama3")
 *     .messages(List.of(ChatMessage.user("안녕하세요?")))
 *     .build();
 *
 * // 대화 기록 포함
 * ChatRequest request = ChatRequest.builder()
 *     .model("llama3")
 *     .messages(List.of(
 *         ChatMessage.system("너는 친절한 어시스턴트야"),
 *         ChatMessage.user("안녕?"),
 *         ChatMessage.assistant("안녕하세요!"),
 *         ChatMessage.user("방금 뭐라고 했어?")
 *     ))
 *     .build();
 * </pre>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * AI 모델명 (필수)
     * 예: llama3, mistral, gemma3:4b
     */
    private String model;

    /**
     * 대화 메시지 목록 (필수)
     * 대화 기록을 유지하려면 이전 메시지들을 포함해야 합니다.
     */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 스트림 모드 사용 여부
     * 기본값: false (전체 응답을 한 번에 받음)
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * JSON 응답 포맷 지정
     * "json" 또는 JSON 스키마 객체
     */
    private Object format;

    /**
     * 모델 메모리 유지 시간
     * 예: "5m", "1h", "-1" (영구)
     */
    @JsonProperty("keep_alive")
    private String keepAlive;

    /**
     * 추가 모델 옵션
     * temperature, top_k, top_p, seed 등
     */
    private Map<String, Object> options;

    /**
     * 도구 목록 (Function Calling용)
     * stream: false 필수
     */
    private List<Tool> tools;

    /**
     * JSON 응답 강제를 위한 스키마 정의 (내부 처리용)
     * 이 필드가 설정되면 format 파라미터로 변환됩니다.
     */
    @JsonIgnore
    private JsonSchema responseSchema;

    /**
     * 도구 정의 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {

        /**
         * 도구 타입 (현재 "function"만 지원)
         */
        @Builder.Default
        private String type = "function";

        /**
         * 함수 정의
         */
        private Function function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Function {

            /**
             * 함수 이름
             */
            private String name;

            /**
             * 함수 설명
             */
            private String description;

            /**
             * 함수 파라미터 스키마 (JSON Schema 형식)
             */
            private Object parameters;
        }
    }
}
