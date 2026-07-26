package kr.suhsaechan.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.SuhAiderRequest;
import kr.suhsaechan.ai.model.SuhAiderResponse;
import kr.suhsaechan.ai.service.StreamCallback;
import kr.suhsaechan.ai.support.MockOllamaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 텍스트 생성 API 테스트
 *
 * <p>핵심은 v2.0의 스키마 처리 방식 변경입니다. 프롬프트를 조작하지 않고
 * 네이티브 {@code format}으로 전달하는지 확인합니다.</p>
 */
class GenerateApiTest extends MockOllamaTest {

    @Test
    @DisplayName("responseSchema를 네이티브 format으로 전송하고 프롬프트는 건드리지 않는다")
    void sendsSchemaAsNativeFormat() throws Exception {
        enqueueJson("{\"response\":\"{\\\"name\\\":\\\"홍길동\\\"}\",\"done\":true}");

        GenerateApi api = new GenerateApi(http, null);
        api.generate(SuhAiderRequest.builder()
                .model("gemma4:e2b")
                .prompt("이름을 뽑아줘")
                .responseSchema(JsonSchema.of("name", "string"))
                .build());

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());

        // 프롬프트 원본 유지 - v1.x는 여기에 영어 지시문을 덧붙였다
        assertEquals("이름을 뽑아줘", body.get("prompt").asText());

        // 스키마가 format으로 전달됨
        assertEquals("object", body.path("format").path("type").asText());
        assertEquals("string", body.path("format").path("properties").path("name").path("type").asText());

        // 내부 필드는 전송되지 않음
        assertFalse(body.has("responseSchema"));
    }

    @Test
    @DisplayName("스키마가 없으면 format을 보내지 않는다")
    void omitsFormatWithoutSchema() throws Exception {
        enqueueJson("{\"response\":\"안녕하세요\",\"done\":true}");

        GenerateApi api = new GenerateApi(http, null);
        api.generate("gemma4:e2b", "안녕?");

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertFalse(body.has("format"), "format이 null로도 전송되면 안 된다");
    }

    @Test
    @DisplayName("전역 기본 스키마를 적용한다")
    void appliesCustomizerDefaultSchema() throws Exception {
        enqueueJson("{\"response\":\"{\\\"ok\\\":true}\",\"done\":true}");

        SuhAiderCustomizer customizer = SuhAiderCustomizer.builder()
                .defaultResponseSchema(JsonSchema.of("ok", "boolean"))
                .build();

        new GenerateApi(http, customizer).generate("gemma4:e2b", "테스트");

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("boolean", body.path("format").path("properties").path("ok").path("type").asText());
    }

    @Test
    @DisplayName("개별 요청 스키마가 전역 기본값보다 우선한다")
    void requestSchemaWinsOverDefault() throws Exception {
        enqueueJson("{\"response\":\"{}\",\"done\":true}");

        SuhAiderCustomizer customizer = SuhAiderCustomizer.builder()
                .defaultResponseSchema(JsonSchema.of("global", "string"))
                .build();

        new GenerateApi(http, customizer).generate(SuhAiderRequest.builder()
                .model("gemma4:e2b")
                .prompt("x")
                .responseSchema(JsonSchema.of("local", "integer"))
                .build());

        JsonNode format = objectMapper.readTree(server.takeRequest().getBody().readUtf8()).path("format");
        assertTrue(format.path("properties").has("local"));
        assertFalse(format.path("properties").has("global"));
    }

    @Test
    @DisplayName("마크다운으로 감싼 JSON 응답을 방어적으로 정제한다")
    void cleansMarkdownWrappedJson() {
        enqueueJson("{\"response\":\"```json\\n{\\\"name\\\":\\\"홍길동\\\"}\\n```\",\"done\":true}");

        SuhAiderResponse response = new GenerateApi(http, null).generate(SuhAiderRequest.builder()
                .model("gemma4:e2b")
                .prompt("x")
                .responseSchema(JsonSchema.of("name", "string"))
                .build());

        assertEquals("{\"name\":\"홍길동\"}", response.getResponse());
    }

    @Test
    @DisplayName("스트리밍에도 format이 적용된다")
    void streamingSupportsSchema() throws Exception {
        enqueueNdjson(
                "{\"response\":\"{\\\"a\\\":\",\"done\":false}",
                "{\"response\":\"1}\",\"done\":true}");

        List<String> chunks = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();

        new GenerateApi(http, null).generateStream(SuhAiderRequest.builder()
                .model("gemma4:e2b")
                .prompt("숫자")
                .responseSchema(JsonSchema.of("a", "integer"))
                .build(), new StreamCallback() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete() {
                completed.incrementAndGet();
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertTrue(body.get("stream").asBoolean(), "스트리밍 요청은 stream:true여야 한다");
        assertEquals("integer", body.path("format").path("properties").path("a").path("type").asText());

        // v1.x는 스트리밍에서 스키마를 무시하고 경고만 찍었다
        assertEquals("{\"a\":1}", String.join("", chunks));
        assertEquals(1, completed.get());
    }

    @Test
    @DisplayName("모델명/프롬프트 누락은 INVALID_PARAMETER로 실패한다")
    void validatesRequiredParameters() {
        GenerateApi api = new GenerateApi(http, null);

        SuhAiderException noModel = assertThrows(SuhAiderException.class,
                () -> api.generate(SuhAiderRequest.builder().prompt("x").build()));
        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER, noModel.getErrorCode());

        SuhAiderException noPrompt = assertThrows(SuhAiderException.class,
                () -> api.generate(SuhAiderRequest.builder().model("m").build()));
        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER, noPrompt.getErrorCode());
    }

    @Test
    @DisplayName("스트리밍 파라미터 오류는 예외 대신 onError로 전달된다")
    void streamingValidationGoesToCallback() {
        AtomicInteger errors = new AtomicInteger();

        new GenerateApi(http, null).generateStream(SuhAiderRequest.builder().build(), new StreamCallback() {
            @Override
            public void onNext(String chunk) {
                // 사용하지 않음
            }

            @Override
            public void onComplete() {
                throw new AssertionError("검증 실패인데 완료 통지가 왔다");
            }

            @Override
            public void onError(Throwable error) {
                errors.incrementAndGet();
            }
        });

        assertEquals(1, errors.get());
        assertEquals(0, server.getRequestCount(), "검증 실패 시 서버로 요청이 나가면 안 된다");
    }
}
