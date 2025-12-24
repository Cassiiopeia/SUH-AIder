package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Function Calling 응답 DTO
 * AI가 선택한 Tool과 인자 정보를 담습니다.
 *
 * <p>사용 예제:</p>
 * <pre>
 * FunctionResponse response = engine.functionCall(request);
 *
 * if (response.hasToolCall()) {
 *     String toolName = response.getToolName();     // "route_rag"
 *     String query = response.getArgumentAsString("query");  // "SSE configuration"
 *
 *     switch (toolName) {
 *         case "route_rag":
 *             // RAG 검색 수행
 *             break;
 *         case "route_system":
 *             String action = response.getArgumentAsString("action");
 *             // 시스템 액션 수행
 *             break;
 *     }
 * } else {
 *     // Tool 선택 실패 - clarify 처리
 * }
 * </pre>
 *
 * @see FunctionRequest
 * @see FunctionTool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class FunctionResponse {

    /**
     * 선택된 Tool 이름
     * 예: "route_rag", "route_system"
     */
    private String toolName;

    /**
     * Tool 인자 맵
     * 예: {query: "SSE configuration", action: "get_status"}
     */
    @Builder.Default
    private Map<String, Object> arguments = Collections.emptyMap();

    /**
     * Tool 호출 존재 여부
     * false이면 AI가 Tool을 선택하지 않은 것
     */
    @Builder.Default
    private boolean hasToolCall = false;

    /**
     * 원본 ChatResponse (디버깅용)
     */
    private ChatResponse rawResponse;

    // ========== 편의 메서드 ==========

    /**
     * 인자를 String으로 가져오기
     *
     * @param key 인자 이름
     * @return 문자열 값 (없으면 null)
     */
    public String getArgumentAsString(String key) {
        if (arguments == null || !arguments.containsKey(key)) {
            return null;
        }
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 인자를 Integer로 가져오기
     *
     * @param key 인자 이름
     * @return Integer 값 (없거나 변환 실패 시 null)
     */
    public Integer getArgumentAsInteger(String key) {
        if (arguments == null || !arguments.containsKey(key)) {
            return null;
        }
        Object value = arguments.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 인자를 Boolean으로 가져오기
     *
     * @param key 인자 이름
     * @return Boolean 값 (없으면 null)
     */
    public Boolean getArgumentAsBoolean(String key) {
        if (arguments == null || !arguments.containsKey(key)) {
            return null;
        }
        Object value = arguments.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * 인자를 List로 가져오기
     *
     * @param key 인자 이름
     * @return List 값 (없으면 빈 리스트)
     */
    @SuppressWarnings("unchecked")
    public List<Object> getArgumentAsList(String key) {
        if (arguments == null || !arguments.containsKey(key)) {
            return Collections.emptyList();
        }
        Object value = arguments.get(key);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return Collections.emptyList();
    }

    /**
     * 특정 인자가 존재하는지 확인
     *
     * @param key 인자 이름
     * @return 존재하면 true
     */
    public boolean hasArgument(String key) {
        return arguments != null && arguments.containsKey(key);
    }

    // ========== 정적 팩토리 ==========

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ChatResponse에서 FunctionResponse 생성
     *
     * @param chatResponse Ollama Chat API 응답
     * @return FunctionResponse 인스턴스
     */
    public static FunctionResponse fromChatResponse(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return FunctionResponse.builder()
                    .hasToolCall(false)
                    .build();
        }

        // tool_calls가 없는 경우
        if (!chatResponse.hasToolCalls()) {
            return FunctionResponse.builder()
                    .hasToolCall(false)
                    .rawResponse(chatResponse)
                    .build();
        }

        // 첫 번째 tool_call 파싱
        List<ChatMessage.ToolCall> toolCalls = chatResponse.getMessage().getToolCalls();
        ChatMessage.ToolCall firstCall = toolCalls.get(0);
        ChatMessage.ToolCall.Function function = firstCall.getFunction();

        String toolName = function.getName();
        Map<String, Object> argsMap = parseArguments(function.getArguments());

        return FunctionResponse.builder()
                .toolName(toolName)
                .arguments(argsMap)
                .hasToolCall(true)
                .rawResponse(chatResponse)
                .build();
    }

    /**
     * arguments 파싱 (JSON 문자열 또는 Map)
     *
     * @param arguments 원본 arguments (String 또는 Map)
     * @return 파싱된 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(Object arguments) {
        if (arguments == null) {
            return Collections.emptyMap();
        }

        // 이미 Map인 경우
        if (arguments instanceof Map) {
            return (Map<String, Object>) arguments;
        }

        // JSON 문자열인 경우
        if (arguments instanceof String) {
            String jsonStr = (String) arguments;
            if (jsonStr.trim().isEmpty()) {
                return Collections.emptyMap();
            }
            try {
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("arguments JSON 파싱 실패: {}", jsonStr, e);
                return Collections.emptyMap();
            }
        }

        log.warn("알 수 없는 arguments 타입: {}", arguments.getClass().getName());
        return Collections.emptyMap();
    }

    // ========== Tool 미선택 시 헬퍼 ==========

    /**
     * Tool 미선택 응답 생성
     * AI가 Tool을 선택하지 않았을 때 사용
     *
     * @param rawResponse 원본 응답
     * @return hasToolCall=false인 FunctionResponse
     */
    public static FunctionResponse noToolCall(ChatResponse rawResponse) {
        return FunctionResponse.builder()
                .hasToolCall(false)
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * 에러 응답 생성
     *
     * @return 빈 FunctionResponse
     */
    public static FunctionResponse empty() {
        return FunctionResponse.builder()
                .hasToolCall(false)
                .build();
    }
}
