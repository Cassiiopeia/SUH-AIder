package kr.suhsaechan.ai.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * MockWebServer 기반 유닛 테스트 공통 베이스
 *
 * <p>실제 Ollama 서버 없이 응답을 흉내 내어 검증합니다. 네트워크에 의존하지 않으므로
 * CI에서 항상 같은 결과가 나옵니다.</p>
 */
public abstract class MockOllamaTest {

    protected MockWebServer server;
    protected SuhAiderConfig config;
    protected SuhAiderHttpExecutor http;
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUpMockServer() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new SuhAiderConfig();
        config.setBaseUrl(server.url("/").toString());  // 후행 슬래시는 setter가 정규화한다
        config.getModelRefresh().setLoadOnStartup(false);

        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();

        http = new SuhAiderHttpExecutor(client, objectMapper, config);
    }

    @AfterEach
    void tearDownMockServer() throws IOException {
        server.shutdown();
    }

    /**
     * JSON 본문으로 200 응답 예약
     *
     * @param json 응답 본문
     */
    protected void enqueueJson(String json) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
    }

    /**
     * NDJSON 스트리밍 응답 예약
     *
     * @param lines 줄 단위 JSON
     */
    protected void enqueueNdjson(String... lines) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(String.join("\n", lines) + "\n"));
    }

    /**
     * 실패 응답 예약
     *
     * @param code HTTP 상태 코드
     * @param body 응답 본문
     */
    protected void enqueueError(int code, String body) {
        server.enqueue(new MockResponse().setResponseCode(code).setBody(body));
    }
}
