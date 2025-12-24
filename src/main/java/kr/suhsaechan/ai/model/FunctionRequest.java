package kr.suhsaechan.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Function Calling 요청 DTO
 * FunctionGemma 등 Function Calling 지원 모델로 사용자 의도를 분류할 때 사용합니다.
 *
 * <p>사용 예제:</p>
 * <pre>
 * // 1. 빌더 템플릿 정의 (한 번)
 * FunctionRequest.Builder myRouter = FunctionRequest.builder()
 *     .model("functiongemma")
 *     .systemPrompt("You are a strict router. Choose exactly ONE tool call.")
 *     .tool(FunctionTool.builder()
 *         .name("route_rag")
 *         .description("Use when user asks about config location")
 *         .parameter(FunctionParameter.required("query", "string", "Search query"))
 *         .build())
 *     .tool(FunctionTool.builder()
 *         .name("route_system")
 *         .description("Use for status/logs")
 *         .parameter(FunctionParameter.enumType("action", "Action", "get_status", "get_logs"))
 *         .build());
 *
 * // 2. 사용 (userText만 추가)
 * FunctionResponse response = engine.functionCall(
 *     myRouter.userText("SSE 설정 어디에 했지?").build()
 * );
 * </pre>
 *
 * @see FunctionTool
 * @see FunctionResponse
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FunctionRequest {

    /**
     * AI 모델명 (필수)
     * 예: "functiongemma", "llama3.1"
     */
    private String model;

    /**
     * 사용자 입력 텍스트 (필수)
     * AI가 분류할 사용자의 질문/요청
     */
    private String userText;

    /**
     * 시스템 프롬프트 (필수)
     * AI의 라우팅 규칙을 정의합니다.
     *
     * <p>예시:</p>
     * <pre>
     * You are a strict router. Choose exactly ONE tool call.
     * Rules:
     * - 설정/위치/어디 → route_rag
     * - 상태/로그/포트 → route_system
     * - 잡담/인사 → route_smalltalk
     * </pre>
     */
    private String systemPrompt;

    /**
     * Tool 목록 (필수)
     * AI가 선택할 수 있는 함수들
     */
    @Builder.Default
    private List<FunctionTool> tools = new ArrayList<>();

    /**
     * 추가 모델 옵션 (선택)
     * temperature, top_k, top_p 등
     */
    private Map<String, Object> options;

    /**
     * 모델 메모리 유지 시간 (선택)
     * 예: "5m", "1h", "-1" (영구)
     */
    private String keepAlive;

    // ========== 빌더 헬퍼 ==========

    /**
     * 커스텀 빌더 - tool() 메서드로 Tool 추가 지원
     */
    public static class FunctionRequestBuilder {

        /**
         * Tool 추가 (체이닝)
         *
         * @param tool 추가할 FunctionTool
         * @return 빌더 인스턴스
         */
        public FunctionRequestBuilder tool(FunctionTool tool) {
            if (this.tools$value == null) {
                this.tools$value = new ArrayList<>();
                this.tools$set = true;
            }
            this.tools$value.add(tool);
            return this;
        }
    }

    // ========== 변환 메서드 ==========

    /**
     * ChatRequest로 변환
     * Ollama /api/chat 요청 형식으로 변환합니다.
     *
     * @return ChatRequest 인스턴스
     */
    public ChatRequest toChatRequest() {
        // FunctionTool → ChatRequest.Tool 변환
        List<ChatRequest.Tool> chatTools = tools.stream()
                .map(FunctionTool::toChatRequestTool)
                .collect(Collectors.toList());

        // 메시지 구성
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(this.systemPrompt));
        messages.add(ChatMessage.user(this.userText));

        return ChatRequest.builder()
                .model(this.model)
                .messages(messages)
                .tools(chatTools)
                .stream(false)  // Function Calling은 stream=false 필수
                .options(this.options)
                .keepAlive(this.keepAlive)
                .build();
    }
}
