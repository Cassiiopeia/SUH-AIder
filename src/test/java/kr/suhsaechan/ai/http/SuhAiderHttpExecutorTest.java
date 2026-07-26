package kr.suhsaechan.ai.http;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.service.StreamCallback;
import kr.suhsaechan.ai.support.MockOllamaTest;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 실행 계층 테스트
 */
class SuhAiderHttpExecutorTest extends MockOllamaTest {

    @Test
    @DisplayName("GET 응답을 지정 타입으로 역직렬화한다")
    void deserializesGetResponse() throws Exception {
        enqueueJson("{\"models\":[{\"name\":\"gemma4:e2b\",\"size\":123}]}");

        ModelListResponse response = http.get("/api/tags", ModelListResponse.class);

        assertEquals(1, response.getModels().size());
        assertEquals("gemma4:e2b", response.getModels().get(0).getName());

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/tags", request.getPath());
        assertEquals("GET", request.getMethod());
    }

    @Test
    @DisplayName("baseUrl에 슬래시가 있어도 경로가 이중 슬래시로 만들어지지 않는다")
    void doesNotProduceDoubleSlash() throws Exception {
        // 정규화가 없으면 //api/tags 가 되어 서버가 404를 낸다
        config.setBaseUrl(server.url("/").toString());
        enqueueJson("{\"models\":[]}");

        http.get("/api/tags", ModelListResponse.class);

        assertEquals("/api/tags", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("API 키가 설정되면 인증 헤더를 붙인다")
    void attachesAuthHeader() throws Exception {
        config.getSecurity().setApiKey("secret-key");
        config.getSecurity().setHeaderName("X-API-Key");
        enqueueJson("{\"models\":[]}");

        http.get("/api/tags", ModelListResponse.class);

        assertEquals("secret-key", server.takeRequest().getHeader("X-API-Key"));
    }

    @Test
    @DisplayName("헤더 값 포맷을 적용한다")
    void appliesHeaderValueFormat() throws Exception {
        config.getSecurity().setApiKey("abc123");
        config.getSecurity().setHeaderName("Authorization");
        config.getSecurity().setHeaderValueFormat("Bearer {value}");
        enqueueJson("{\"models\":[]}");

        http.get("/api/tags", ModelListResponse.class);

        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    @DisplayName("API 키가 없으면 인증 헤더를 붙이지 않는다")
    void omitsAuthHeaderWhenNoKey() throws Exception {
        enqueueJson("{\"models\":[]}");

        http.get("/api/tags", ModelListResponse.class);

        assertNull(server.takeRequest().getHeader("X-API-Key"));
    }

    @Test
    @DisplayName("빈 본문은 EMPTY_RESPONSE로 실패한다")
    void failsOnEmptyBody() {
        enqueueJson("");

        SuhAiderException e = assertThrows(SuhAiderException.class,
                () -> http.get("/api/tags", ModelListResponse.class));
        assertEquals(SuhAiderErrorCode.EMPTY_RESPONSE, e.getErrorCode());
    }

    @Test
    @DisplayName("깨진 JSON은 JSON_PARSE_ERROR로 실패한다")
    void failsOnMalformedJson() {
        enqueueJson("{not json");

        SuhAiderException e = assertThrows(SuhAiderException.class,
                () -> http.get("/api/tags", ModelListResponse.class));
        assertEquals(SuhAiderErrorCode.JSON_PARSE_ERROR, e.getErrorCode());
    }

    @Test
    @DisplayName("HTTP 오류 상태를 도메인 예외로 변환한다")
    void mapsErrorStatus() {
        enqueueError(401, "unauthorized");

        SuhAiderException e = assertThrows(SuhAiderException.class,
                () -> http.get("/api/tags", ModelListResponse.class));
        assertEquals(SuhAiderErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("DELETE는 작업별 실패 코드를 사용한다")
    void deleteUsesFailureCode() throws Exception {
        enqueueError(500, "boom");

        SuhAiderException e = assertThrows(SuhAiderException.class,
                () -> http.delete("/api/delete", Map.of("name", "x"), SuhAiderErrorCode.MODEL_DELETE_FAILED));
        assertEquals(SuhAiderErrorCode.MODEL_DELETE_FAILED, e.getErrorCode());

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("\"name\":\"x\""));
    }

    @Test
    @DisplayName("스트리밍은 done을 만나면 onComplete를 한 번 호출한다")
    void streamsUntilDone() {
        enqueueNdjson(
                "{\"response\":\"안\",\"done\":false}",
                "{\"response\":\"녕\",\"done\":false}",
                "{\"response\":\"\",\"done\":true}");

        List<String> chunks = new ArrayList<>();
        AtomicInteger completeCount = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();

        http.streamNdjson("/api/generate", Map.of(),
                node -> node.path("response").asText(""),
                callback(chunks, completeCount, error));

        assertEquals(List.of("안", "녕"), chunks);
        assertEquals(1, completeCount.get());
        assertNull(error.get());
    }

    @Test
    @DisplayName("스트림 중간의 깨진 줄은 건너뛰고 계속 처리한다")
    void skipsMalformedLines() {
        enqueueNdjson(
                "{\"response\":\"A\",\"done\":false}",
                "!!! not json !!!",
                "{\"response\":\"B\",\"done\":true}");

        List<String> chunks = new ArrayList<>();
        AtomicInteger completeCount = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();

        http.streamNdjson("/api/generate", Map.of(),
                node -> node.path("response").asText(""),
                callback(chunks, completeCount, error));

        assertEquals(List.of("A", "B"), chunks);
        assertEquals(1, completeCount.get());
    }

    @Test
    @DisplayName("onNext에서 예외가 나도 스트림이 끝까지 진행되고 종료 통지가 나간다")
    void isolatesConsumerCallbackFailure() {
        enqueueNdjson(
                "{\"response\":\"A\",\"done\":false}",
                "{\"response\":\"B\",\"done\":true}");

        AtomicInteger completeCount = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();

        http.streamNdjson("/api/generate", Map.of(),
                node -> node.path("response").asText(""),
                new StreamCallback() {
                    @Override
                    public void onNext(String chunk) {
                        throw new IllegalStateException("소비자 코드 버그");
                    }

                    @Override
                    public void onComplete() {
                        completeCount.incrementAndGet();
                    }

                    @Override
                    public void onError(Throwable e) {
                        error.set(e);
                    }
                });

        assertEquals(1, completeCount.get());
        assertNull(error.get(), "소비자 콜백 예외는 스트림 실패로 취급하지 않는다");
    }

    @Test
    @DisplayName("스트리밍 시작이 실패하면 onError가 한 번 호출된다")
    void reportsStreamStartFailure() {
        enqueueError(500, "server down");

        List<String> chunks = new ArrayList<>();
        AtomicInteger completeCount = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();

        http.streamNdjson("/api/generate", Map.of(),
                node -> node.path("response").asText(""),
                callback(chunks, completeCount, error));

        assertEquals(0, completeCount.get());
        assertTrue(error.get() instanceof SuhAiderException);
        assertEquals(SuhAiderErrorCode.SERVER_ERROR, ((SuhAiderException) error.get()).getErrorCode());
    }

    private StreamCallback callback(List<String> chunks, AtomicInteger completeCount,
                                    AtomicReference<Throwable> error) {
        return new StreamCallback() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete() {
                completeCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
        };
    }
}
