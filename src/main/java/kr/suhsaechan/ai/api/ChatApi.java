package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.service.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 대화형 API (Ollama {@code /api/chat})
 *
 * <p>이전 대화 기록을 messages에 포함하면 컨텍스트가 유지됩니다.</p>
 */
@Slf4j
public class ChatApi {

    private static final String PATH = "/api/chat";

    private final SuhAiderHttpExecutor http;
    private final SuhAiderCustomizer customizer;

    public ChatApi(SuhAiderHttpExecutor http, SuhAiderCustomizer customizer) {
        this.http = http;
        this.customizer = customizer;
    }

    /**
     * 대화형 응답 생성
     *
     * @param request 요청 (model, messages 필수)
     * @return 응답
     * @throws SuhAiderException 파라미터 오류, 통신 오류 시
     */
    public ChatResponse chat(ChatRequest request) {
        validate(request);

        JsonSchema schema = SchemaSupport.resolve(request.getResponseSchema(), customizer);
        ChatRequest payload = toPayload(request, schema, false);

        log.debug("Chat 호출 - 모델: {}, 메시지 수: {}", request.getModel(), request.getMessages().size());

        ChatResponse response = http.post(PATH, payload, ChatResponse.class);

        if (schema != null && response.getMessage() != null) {
            response.getMessage().setContent(
                    SchemaSupport.cleanJsonResponse(response.getMessage().getContent(), http.objectMapper()));
        }

        log.info("Chat 완료 - 응답 길이: {}, 처리 시간: {}ms",
                response.getContent() != null ? response.getContent().length() : 0,
                response.getTotalDurationMs() != null ? response.getTotalDurationMs() : 0);

        return response;
    }

    /**
     * 간편 Chat (메시지 목록)
     *
     * @param model    모델명
     * @param messages 대화 메시지 목록
     * @return 응답
     */
    public ChatResponse chat(String model, List<ChatMessage> messages) {
        return chat(ChatRequest.builder()
                .model(model)
                .messages(messages)
                .stream(false)
                .build());
    }

    /**
     * 단일 메시지 Chat
     *
     * @param model       모델명
     * @param userMessage 사용자 메시지
     * @return 응답 텍스트
     */
    public String chat(String model, String userMessage) {
        return chat(model, List.of(ChatMessage.user(userMessage))).getContent();
    }

    /**
     * 시스템 프롬프트 포함 Chat
     *
     * @param model        모델명
     * @param systemPrompt 시스템 지시문
     * @param userMessage  사용자 메시지
     * @return 응답 텍스트
     */
    public String chat(String model, String systemPrompt, String userMessage) {
        return chat(model, List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(userMessage))).getContent();
    }

    /**
     * 스트리밍 Chat
     *
     * @param request  요청 (model, messages 필수)
     * @param callback 스트리밍 콜백
     */
    public void chatStream(ChatRequest request, StreamCallback callback) {
        try {
            validate(request);
        } catch (SuhAiderException e) {
            callback.onError(e);
            return;
        }

        JsonSchema schema = SchemaSupport.resolve(request.getResponseSchema(), customizer);
        ChatRequest payload = toPayload(request, schema, true);

        log.debug("Chat Stream 호출 - 모델: {}, 메시지 수: {}", request.getModel(), request.getMessages().size());

        http.streamNdjson(PATH, payload,
                node -> node.path("message").path("content").asText(""), callback);
    }

    /**
     * 간편 스트리밍 Chat
     *
     * @param model    모델명
     * @param messages 대화 메시지 목록
     * @param callback 스트리밍 콜백
     */
    public void chatStream(String model, List<ChatMessage> messages, StreamCallback callback) {
        chatStream(ChatRequest.builder()
                .model(model)
                .messages(messages)
                .build(), callback);
    }

    private void validate(ChatRequest request) {
        if (request == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "요청이 null입니다");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "메시지가 비어있습니다");
        }
    }

    /**
     * 전송용 요청 생성 — 스키마를 format으로 변환하고 내부 필드를 제거
     */
    private ChatRequest toPayload(ChatRequest request, JsonSchema schema, boolean stream) {
        Object format = request.getFormat() != null
                ? request.getFormat()                              // 직접 지정한 format이 우선
                : (schema != null ? schema.toFormatObject() : null);

        return request.toBuilder()
                .stream(stream)
                .format(format)
                .responseSchema(null)  // Ollama로 전송하지 않는 내부 필드
                .build();
    }
}
