package kr.suhsaechan.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.service.StreamCallback;
import kr.suhsaechan.ai.support.MockOllamaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 대화 API 테스트
 */
class ChatApiTest extends MockOllamaTest {

    @Test
    @DisplayName("대화 기록을 그대로 전송하고 응답 내용을 꺼낸다")
    void sendsMessagesAndReadsContent() throws Exception {
        enqueueJson("{\"message\":{\"role\":\"assistant\",\"content\":\"안녕하세요!\"},\"done\":true}");

        ChatResponse response = new ChatApi(http, null).chat("gemma4:e2b", List.of(
                ChatMessage.system("친절하게"),
                ChatMessage.user("안녕?")));

        assertEquals("안녕하세요!", response.getContent());

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(2, body.get("messages").size());
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("안녕?", body.get("messages").get(1).get("content").asText());
    }

    @Test
    @DisplayName("responseSchema를 format으로 변환해 전송한다")
    void convertsSchemaToFormat() throws Exception {
        enqueueJson("{\"message\":{\"content\":\"{\\\"name\\\":\\\"홍길동\\\"}\"},\"done\":true}");

        new ChatApi(http, null).chat(ChatRequest.builder()
                .model("gemma4:e2b")
                .messages(List.of(ChatMessage.user("이름")))
                .responseSchema(JsonSchema.of("name", "string"))
                .build());

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("string", body.path("format").path("properties").path("name").path("type").asText());
    }

    @Test
    @DisplayName("tool_calls의 id를 유실하지 않는다")
    void preservesToolCallId() {
        // v1.x는 ToolCall에 id 필드가 없어 서버가 준 값이 버려졌다
        enqueueJson("{\"message\":{\"role\":\"assistant\",\"tool_calls\":["
                + "{\"id\":\"call_abc123\",\"function\":{\"name\":\"route_rag\",\"arguments\":{\"query\":\"x\"}}}"
                + "]},\"done\":true}");

        ChatResponse response = new ChatApi(http, null)
                .chat("gemma4:e2b", List.of(ChatMessage.user("설정 어디?")));

        assertTrue(response.hasToolCalls());
        ChatMessage.ToolCall call = response.getMessage().getToolCalls().get(0);
        assertNotNull(call.getId());
        assertEquals("call_abc123", call.getId());
        assertEquals("route_rag", call.getFunction().getName());
    }

    @Test
    @DisplayName("스트리밍은 message.content에서 조각을 뽑는다")
    void streamsMessageContent() {
        enqueueNdjson(
                "{\"message\":{\"content\":\"안\"},\"done\":false}",
                "{\"message\":{\"content\":\"녕\"},\"done\":false}",
                "{\"message\":{\"content\":\"\"},\"done\":true}");

        List<String> chunks = new ArrayList<>();
        new ChatApi(http, null).chatStream("gemma4:e2b", List.of(ChatMessage.user("안녕?")),
                new StreamCallback() {
                    @Override
                    public void onNext(String chunk) {
                        chunks.add(chunk);
                    }

                    @Override
                    public void onComplete() {
                        // 검증은 chunks로 한다
                    }

                    @Override
                    public void onError(Throwable error) {
                        throw new AssertionError(error);
                    }
                });

        assertEquals("안녕", String.join("", chunks));
    }

    @Test
    @DisplayName("model/messages 누락은 INVALID_PARAMETER로 실패한다")
    void validatesRequiredParameters() {
        ChatApi api = new ChatApi(http, null);

        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER,
                assertThrows(SuhAiderException.class, () -> api.chat(ChatRequest.builder()
                        .messages(List.of(ChatMessage.user("x"))).build())).getErrorCode());

        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER,
                assertThrows(SuhAiderException.class, () -> api.chat(ChatRequest.builder()
                        .model("m").messages(List.of()).build())).getErrorCode());
    }
}
