package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SUH-AIDER Chat API 메시지 DTO
 * Ollama /api/chat 메시지 형식
 *
 * <p>사용 예제:</p>
 * <pre>
 * // 시스템 메시지
 * ChatMessage.system("너는 친절한 어시스턴트야");
 *
 * // 사용자 메시지
 * ChatMessage.user("안녕하세요?");
 *
 * // 어시스턴트 메시지 (대화 기록용)
 * ChatMessage.assistant("안녕하세요! 무엇을 도와드릴까요?");
 * </pre>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 메시지 역할
     * system: 시스템 지시문 (AI 행동 지침)
     * user: 사용자 입력
     * assistant: AI 응답
     * tool: 도구 호출 결과
     */
    private String role;

    /**
     * 메시지 내용
     */
    private String content;

    /**
     * 이미지 목록 (멀티모달 모델용)
     * Base64 인코딩된 이미지 데이터
     */
    private List<String> images;

    /**
     * 도구 호출 목록 (assistant 역할에서 사용)
     */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    // ========== 팩토리 메서드 ==========

    /**
     * 시스템 메시지 생성
     * AI의 행동 지침을 설정합니다.
     *
     * @param content 시스템 지시문
     * @return 시스템 메시지
     */
    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role("system")
                .content(content)
                .build();
    }

    /**
     * 사용자 메시지 생성
     *
     * @param content 사용자 입력 텍스트
     * @return 사용자 메시지
     */
    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .build();
    }

    /**
     * 사용자 메시지 생성 (이미지 포함)
     * 멀티모달 모델 (llava 등)에서 사용
     *
     * @param content 사용자 입력 텍스트
     * @param images Base64 인코딩된 이미지 목록
     * @return 사용자 메시지
     */
    public static ChatMessage user(String content, List<String> images) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .images(images)
                .build();
    }

    /**
     * 어시스턴트 메시지 생성
     * 대화 기록을 구성할 때 이전 AI 응답을 추가하는 용도
     *
     * @param content AI 응답 텍스트
     * @return 어시스턴트 메시지
     */
    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .build();
    }

    /**
     * 도구 결과 메시지 생성
     * 도구 호출 결과를 AI에게 전달할 때 사용
     *
     * @param content 도구 실행 결과
     * @return 도구 메시지
     */
    public static ChatMessage tool(String content) {
        return ChatMessage.builder()
                .role("tool")
                .content(content)
                .build();
    }

    /**
     * 도구 호출 정보 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        /**
         * 호출할 함수 정보
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
             * 함수 인자 (JSON 문자열)
             */
            private Object arguments;
        }
    }
}
