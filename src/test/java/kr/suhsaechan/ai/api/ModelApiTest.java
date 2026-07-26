package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ModelInfo;
import kr.suhsaechan.ai.support.MockOllamaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모델 조회·캐시·삭제 API 테스트
 */
class ModelApiTest extends MockOllamaTest {

    @Test
    @DisplayName("이미 꺼내 간 모델 목록은 이후 캐시 변경에 영향받지 않는다")
    void returnedListIsImmutableSnapshot() {
        enqueueJson("{\"models\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");

        ModelApi api = new ModelApi(http, config);
        api.initialize();

        List<ModelInfo> snapshot = api.getAvailableModels();
        assertEquals(2, snapshot.size());

        // 다운로드 완료 스레드가 캐시를 갱신하는 상황
        api.addToCache("c");

        // v1.x는 unmodifiableList 뷰를 반환해서, 이 시점에 snapshot이 3개로 변하고
        // 순회 중이던 소비자는 ConcurrentModificationException을 맞았다
        assertEquals(2, snapshot.size(), "이전에 반환한 목록은 변하면 안 된다");
        assertEquals(3, api.getAvailableModels().size(), "새로 조회하면 갱신된 목록이 나온다");
    }

    @Test
    @DisplayName("반환된 목록은 수정할 수 없다")
    void returnedListIsUnmodifiable() {
        enqueueJson("{\"models\":[{\"name\":\"a\"}]}");

        ModelApi api = new ModelApi(http, config);
        api.initialize();

        assertThrows(UnsupportedOperationException.class,
                () -> api.getAvailableModels().add(ModelInfo.builder().name("hack").build()));
    }

    @Test
    @DisplayName("캐시 미초기화 시 isModelAvailable은 true, getModelInfo는 empty를 반환한다")
    void uninitializedCacheSemantics() {
        ModelApi api = new ModelApi(http, config);

        assertFalse(api.isModelsInitialized());
        assertTrue(api.isModelAvailable("whatever"), "판단 불가 시 서버 검증에 맡긴다");
        assertTrue(api.getModelInfo("whatever").isEmpty(), "캐시에 정보가 없으므로 empty");
        assertTrue(api.getAvailableModels().isEmpty());
    }

    @Test
    @DisplayName("초기화 후에는 캐시에 있는 모델만 available로 판정한다")
    void initializedCacheChecksMembership() {
        enqueueJson("{\"models\":[{\"name\":\"gemma4:e2b\"}]}");

        ModelApi api = new ModelApi(http, config);
        api.initialize();

        assertTrue(api.isModelAvailable("gemma4:e2b"));
        assertFalse(api.isModelAvailable("missing-model"));
        assertTrue(api.getModelInfo("gemma4:e2b").isPresent());
    }

    @Test
    @DisplayName("서버 오류로 초기화에 실패해도 예외를 던지지 않는다")
    void initializeSwallowsFailure() {
        enqueueError(500, "down");

        ModelApi api = new ModelApi(http, config);
        api.initialize();

        assertFalse(api.isModelsInitialized());
        assertTrue(api.getAvailableModels().isEmpty());
    }

    @Test
    @DisplayName("삭제하면 캐시에서도 제거된다")
    void deleteRemovesFromCache() {
        enqueueJson("{\"models\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");
        ModelApi api = new ModelApi(http, config);
        api.initialize();

        enqueueJson("{}");
        assertTrue(api.deleteModel("a"));

        assertEquals(1, api.getAvailableModels().size());
        assertFalse(api.isModelAvailable("a"));
    }

    @Test
    @DisplayName("캐시에 없는 모델 삭제는 MODEL_NOT_FOUND로 실패한다")
    void deleteUnknownModelFails() {
        enqueueJson("{\"models\":[{\"name\":\"a\"}]}");
        ModelApi api = new ModelApi(http, config);
        api.initialize();

        SuhAiderException e = assertThrows(SuhAiderException.class, () -> api.deleteModel("ghost"));
        assertEquals(SuhAiderErrorCode.MODEL_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("checkExists=false면 캐시 확인 없이 삭제를 시도한다")
    void deleteSkipsCacheCheck() {
        enqueueJson("{\"models\":[{\"name\":\"a\"}]}");
        ModelApi api = new ModelApi(http, config);
        api.initialize();

        enqueueJson("{}");
        assertTrue(api.deleteModel("ghost", false));
    }

    @Test
    @DisplayName("빈 모델명 삭제는 INVALID_PARAMETER로 실패한다")
    void deleteRejectsBlankName() {
        ModelApi api = new ModelApi(http, config);

        SuhAiderException e = assertThrows(SuhAiderException.class, () -> api.deleteModel("  "));
        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER, e.getErrorCode());
    }

    @Test
    @DisplayName("Health Check는 Ollama 응답 문구로 판정하고 실패 시 false를 반환한다")
    void healthCheck() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody("Ollama is running"));
        assertTrue(new ModelApi(http, config).isHealthy());

        enqueueError(503, "down");
        assertFalse(new ModelApi(http, config).isHealthy());
    }
}
