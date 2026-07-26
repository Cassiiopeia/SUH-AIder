package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderExecutors;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import kr.suhsaechan.ai.service.PullCallback;
import kr.suhsaechan.ai.service.PullHandle;
import kr.suhsaechan.ai.support.MockOllamaTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모델 다운로드 API 테스트
 *
 * <p>핵심은 <b>종료 통지가 정확히 한 번</b> 나가는지입니다.
 * v1.x는 실패 경로가 {@code onError}와 {@code onComplete}로 갈렸고,
 * 예상 못 한 예외에서는 둘 다 누락됐습니다.</p>
 */
class PullApiTest extends MockOllamaTest {

    private SuhAiderExecutors executors;
    private ModelApi modelApi;
    private PullApi pullApi;

    @BeforeEach
    void setUpPullApi() {
        executors = new SuhAiderExecutors(2);
        modelApi = new ModelApi(http, config);
        pullApi = new PullApi(http, modelApi, executors, config);
    }

    @AfterEach
    void tearDownPullApi() {
        executors.shutdown();
    }

    @Test
    @DisplayName("진행률을 전달하고 success에서 onComplete를 한 번 호출한다")
    void reportsProgressAndCompletesOnce() throws Exception {
        enqueueNdjson(
                "{\"status\":\"pulling manifest\"}",
                "{\"status\":\"downloading\",\"completed\":50,\"total\":100}",
                "{\"status\":\"success\"}");

        Recorder recorder = new Recorder();
        pullApi.pullModelStream("test-model", recorder);

        assertTrue(recorder.await(), "종료 통지를 받지 못했다");
        assertEquals(1, recorder.completeCount.get(), "onComplete는 정확히 1회여야 한다");
        assertTrue(recorder.result.get().isSuccess());
        assertEquals(3, recorder.progresses.size());
        assertEquals(50.0, recorder.progresses.get(1).getPercent());
    }

    @Test
    @DisplayName("본문 error 필드도 onComplete(실패)로 전달된다")
    void serverErrorFieldGoesToOnComplete() throws Exception {
        enqueueNdjson("{\"error\":\"model not found\"}");

        Recorder recorder = new Recorder();
        pullApi.pullModelStream("nope", recorder);

        assertTrue(recorder.await());
        assertEquals(1, recorder.completeCount.get());

        PullResult result = recorder.result.get();
        assertFalse(result.isSuccess());
        assertFalse(result.isCancelled());
        assertEquals("model not found", result.getErrorMessage());
    }

    @Test
    @DisplayName("HTTP 오류도 onComplete(실패)로 전달되며 원인 예외가 담긴다")
    void httpErrorGoesToOnComplete() throws Exception {
        // v1.x는 이 경로만 onError로 보내서, onComplete만 구현한 코드가 결과를 놓쳤다
        enqueueError(500, "boom");

        Recorder recorder = new Recorder();
        pullApi.pullModelStream("test-model", recorder);

        assertTrue(recorder.await());
        assertEquals(1, recorder.completeCount.get());

        PullResult result = recorder.result.get();
        assertFalse(result.isSuccess());
        assertNotNull(result.getCause(), "실패 원인 예외가 PullResult에 담겨야 한다");
    }

    @Test
    @DisplayName("모델명이 비면 요청 없이 실패 결과를 통지한다")
    void rejectsBlankModelName() throws Exception {
        Recorder recorder = new Recorder();
        PullHandle handle = pullApi.pullModelStream("  ", recorder);

        assertTrue(recorder.await());
        assertEquals(1, recorder.completeCount.get());
        assertFalse(recorder.result.get().isSuccess());
        assertTrue(handle.isDone());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("스트림이 success 없이 끝나면 실패로 판정한다")
    void incompleteStreamIsFailure() throws Exception {
        enqueueNdjson("{\"status\":\"downloading\",\"completed\":10,\"total\":100}");

        Recorder recorder = new Recorder();
        pullApi.pullModelStream("test-model", recorder);

        assertTrue(recorder.await());
        assertFalse(recorder.result.get().isSuccess());
        assertEquals("다운로드가 완료되지 않았습니다", recorder.result.get().getErrorMessage());
    }

    @Test
    @DisplayName("onProgress에서 예외가 나도 다운로드는 끝까지 진행된다")
    void isolatesProgressCallbackFailure() throws Exception {
        enqueueNdjson(
                "{\"status\":\"downloading\",\"completed\":1,\"total\":2}",
                "{\"status\":\"success\"}");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PullResult> result = new AtomicReference<>();

        pullApi.pullModelStream("test-model", new PullCallback() {
            @Override
            public void onProgress(PullProgress progress) {
                throw new IllegalStateException("소비자 코드 버그");
            }

            @Override
            public void onComplete(PullResult r) {
                result.set(r);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(result.get().isSuccess());
    }

    @Test
    @DisplayName("성공하면 모델 캐시에 추가된다")
    void addsToCacheOnSuccess() throws Exception {
        enqueueJson("{\"models\":[{\"name\":\"existing\"}]}");
        modelApi.initialize();
        enqueueNdjson("{\"status\":\"success\"}");

        Recorder recorder = new Recorder();
        pullApi.pullModelStream("newly-pulled", recorder);
        assertTrue(recorder.await());

        assertTrue(modelApi.isModelAvailable("newly-pulled"));
        assertEquals(2, modelApi.getAvailableModels().size());
    }

    @Test
    @DisplayName("비동기 다운로드는 실패도 예외가 아닌 결과로 전달한다")
    void asyncReturnsResultOnFailure() throws Exception {
        enqueueNdjson("{\"error\":\"disk full\"}");

        PullResult result = pullApi.pullModelAsync("test-model").get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals("disk full", result.getErrorMessage());
    }

    @Test
    @DisplayName("동기 다운로드는 실패 시 MODEL_PULL_FAILED를 던진다")
    void syncThrowsOnFailure() {
        enqueueNdjson("{\"error\":\"disk full\"}");

        SuhAiderException e = assertThrows(SuhAiderException.class, () -> pullApi.pullModel("test-model"));
        assertEquals(SuhAiderErrorCode.MODEL_PULL_FAILED, e.getErrorCode());
    }

    @Test
    @DisplayName("동기 다운로드는 성공 시 true를 반환한다")
    void syncReturnsTrueOnSuccess() {
        enqueueNdjson("{\"status\":\"success\"}");

        assertTrue(pullApi.pullModel("test-model"));
    }

    @Test
    @DisplayName("동기 다운로드는 설정된 타임아웃을 넘기면 MODEL_PULL_TIMEOUT을 던진다")
    void syncTimesOut() {
        // 응답을 예약하지 않으면 MockWebServer가 대기하므로 타임아웃 경로를 탄다.
        // v1.x는 무제한 대기라 이 상황에서 스레드가 영원히 묶였다.
        config.getPull().setTimeout(java.time.Duration.ofMillis(300));

        SuhAiderException e = assertThrows(SuhAiderException.class, () -> pullApi.pullModel("test-model"));
        assertEquals(SuhAiderErrorCode.MODEL_PULL_TIMEOUT, e.getErrorCode());
    }

    /**
     * 콜백 호출을 기록하는 테스트 도우미
     */
    private static class Recorder implements PullCallback {

        final List<PullProgress> progresses = new ArrayList<>();
        final AtomicInteger completeCount = new AtomicInteger();
        final AtomicReference<PullResult> result = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onProgress(PullProgress progress) {
            progresses.add(progress);
        }

        @Override
        public void onComplete(PullResult r) {
            completeCount.incrementAndGet();
            result.set(r);
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }
}
