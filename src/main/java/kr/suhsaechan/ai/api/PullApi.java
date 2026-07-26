package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.config.SuhAiderExecutors;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.http.NdjsonStreamReader;
import kr.suhsaechan.ai.http.PreparedStream;
import kr.suhsaechan.ai.http.StreamSession;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import kr.suhsaechan.ai.service.CompletedPullHandle;
import kr.suhsaechan.ai.service.DefaultPullHandle;
import kr.suhsaechan.ai.service.PullCallback;
import kr.suhsaechan.ai.service.PullHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 모델 다운로드 API (Ollama {@code /api/pull})
 *
 * <h3>종료 보장</h3>
 * <p>어떤 경로로 끝나든 {@link PullCallback#onComplete(PullResult)}가 정확히 한 번 호출됩니다.
 * v1.x는 {@code IOException}만 잡아서 그 외 런타임 예외가 나면 종료 통지가 아예 누락됐고,
 * 이를 기다리던 동기 {@code pullModel()}이 영구 블로킹됐습니다.</p>
 *
 * <h3>스레드</h3>
 * <p>모든 백그라운드 작업은 전용 스레드풀에서 실행됩니다. 공용 {@code ForkJoinPool}을 쓰면
 * 수십 분짜리 다운로드가 소비자 앱 전체의 병렬 처리를 막습니다.</p>
 */
@Slf4j
public class PullApi {

    private static final String PATH = "/api/pull";

    /**
     * 다운로드는 수십 분이 걸릴 수 있어 읽기 타임아웃을 무제한으로 둔다
     */
    private static final int UNLIMITED_READ_TIMEOUT = 0;

    private final SuhAiderHttpExecutor http;
    private final ModelApi modelApi;
    private final SuhAiderExecutors executors;
    private final SuhAiderConfig config;

    public PullApi(SuhAiderHttpExecutor http, ModelApi modelApi,
                   SuhAiderExecutors executors, SuhAiderConfig config) {
        this.http = http;
        this.modelApi = modelApi;
        this.executors = executors;
        this.config = config;
    }

    /**
     * 모델 다운로드 (동기)
     *
     * @param modelName 다운로드할 모델명
     * @return 성공 시 true
     * @throws SuhAiderException 실패, 취소, 타임아웃 시
     */
    public boolean pullModel(String modelName) {
        return pullModel(modelName, false);
    }

    /**
     * 모델 다운로드 (동기, insecure 옵션)
     *
     * <p>{@code suh.aider.pull.timeout}(기본 60분)까지 대기합니다.
     * v1.x는 무제한 대기라 서버가 응답을 멈추면 스레드가 영원히 묶였습니다.</p>
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기 (보안 위험, 비권장)
     * @return 성공 시 true
     * @throws SuhAiderException 실패, 취소, 타임아웃 시
     */
    public boolean pullModel(String modelName, boolean insecure) {
        log.info("모델 다운로드 시작 (동기) - 모델: {}, insecure: {}", modelName, insecure);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PullResult> resultRef = new AtomicReference<>();

        pullModelStream(modelName, insecure, new PullCallback() {
            @Override
            public void onProgress(PullProgress progress) {
                log.debug("다운로드 진행: {} - {}", modelName, progress.getFormattedProgress());
            }

            @Override
            public void onComplete(PullResult result) {
                resultRef.set(result);
                latch.countDown();
            }
        });

        long timeoutMs = config.getPull().getTimeout().toMillis();
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_TIMEOUT,
                        modelName + " (" + config.getPull().getTimeout() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED, "다운로드가 인터럽트되었습니다", e);
        }

        PullResult result = resultRef.get();

        if (result.isCancelled()) {
            throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_CANCELLED, modelName);
        }
        if (!result.isSuccess()) {
            throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED,
                    result.getErrorMessage(), result.getCause());
        }

        log.info("모델 다운로드 완료 - 모델: {}, 소요시간: {}", modelName, result.getFormattedDuration());
        return true;
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능)
     *
     * @param modelName 다운로드할 모델명
     * @param callback  진행률/종료 콜백
     * @return 취소 및 상태 확인용 핸들
     */
    public PullHandle pullModelStream(String modelName, PullCallback callback) {
        return pullModelStream(modelName, false, callback);
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능, insecure 옵션)
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기
     * @param callback  진행률/종료 콜백
     * @return 취소 및 상태 확인용 핸들
     */
    public PullHandle pullModelStream(String modelName, boolean insecure, PullCallback callback) {
        log.info("모델 다운로드 시작 (스트림) - 모델: {}, insecure: {}", modelName, insecure);

        if (!StringUtils.hasText(modelName)) {
            // 통신을 시작하지 못한 경우에도 종료 계약은 지킨다
            callback.onComplete(PullResult.failure(modelName, "모델명이 비어있습니다",
                    new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다")));
            return new CompletedPullHandle(modelName);
        }

        // Ollama /api/pull은 insecure=false도 명시적으로 받으므로 순서 보존 맵을 쓴다
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", modelName);
        payload.put("insecure", insecure);
        payload.put("stream", true);

        PreparedStream prepared = http.prepareStream(PATH, payload,
                UNLIMITED_READ_TIMEOUT, SuhAiderErrorCode.MODEL_PULL_FAILED);
        DefaultPullHandle handle = new DefaultPullHandle(modelName, prepared);

        executors.executor().execute(() -> runPull(modelName, prepared, handle, callback));

        return handle;
    }

    /**
     * 모델 다운로드 (비동기)
     *
     * @param modelName 다운로드할 모델명
     * @return 결과 Future (실패도 예외가 아닌 결과로 전달됨)
     */
    public CompletableFuture<PullResult> pullModelAsync(String modelName) {
        return pullModelAsync(modelName, null);
    }

    /**
     * 모델 다운로드 (비동기 + 진행률 리스너)
     *
     * <p>실패도 {@code PullResult}로 전달되므로 {@code future.get()}이 예외를 던지지 않습니다.
     * 결과 종류는 {@code isSuccess()} / {@code isCancelled()}로 판별하세요.</p>
     *
     * @param modelName        다운로드할 모델명
     * @param progressListener 진행률 리스너 (null 가능)
     * @return 결과 Future
     */
    public CompletableFuture<PullResult> pullModelAsync(String modelName, Consumer<PullProgress> progressListener) {
        CompletableFuture<PullResult> future = new CompletableFuture<>();

        pullModelStream(modelName, new PullCallback() {
            @Override
            public void onProgress(PullProgress progress) {
                if (progressListener != null) {
                    progressListener.accept(progress);
                }
            }

            @Override
            public void onComplete(PullResult result) {
                future.complete(result);
            }
        });

        return future;
    }

    /**
     * 여러 모델 병렬 다운로드
     *
     * @param modelNames 다운로드할 모델명 목록
     * @param callback   각 모델의 진행률/종료 콜백
     * @return 각 모델에 대한 핸들 목록
     */
    public List<PullHandle> pullModelsParallel(List<String> modelNames, PullCallback callback) {
        log.info("병렬 모델 다운로드 시작 - 모델 {}개: {}", modelNames.size(), modelNames);

        return modelNames.stream()
                .map(name -> pullModelStream(name, callback))
                .collect(Collectors.toList());
    }

    /**
     * 여러 모델 병렬 다운로드 (비동기)
     *
     * @param modelNames 다운로드할 모델명 목록
     * @return 모든 결과가 모이면 완료되는 Future
     */
    public CompletableFuture<List<PullResult>> pullModelsAsync(List<String> modelNames) {
        log.info("비동기 병렬 모델 다운로드 시작 - 모델 {}개: {}", modelNames.size(), modelNames);

        List<CompletableFuture<PullResult>> futures = modelNames.stream()
                .map(this::pullModelAsync)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    // ========== 내부 구현 ==========

    /**
     * 백그라운드 다운로드 본체
     *
     * <p>어떤 예외가 나도 마지막에 {@code onComplete}가 정확히 한 번 호출되도록
     * 전체를 감쌉니다.</p>
     */
    private void runPull(String modelName, PreparedStream prepared,
                         DefaultPullHandle handle, PullCallback callback) {
        long startTime = System.currentTimeMillis();
        PullResult result;

        try (StreamSession session = prepared.open()) {
            result = readStream(modelName, session, prepared, handle, callback, startTime);

        } catch (Throwable t) {
            if (prepared.isCancelled()) {
                log.info("모델 다운로드 취소됨: {}", modelName);
                result = PullResult.cancelled(modelName);
            } else {
                log.error("모델 다운로드 실패 - {}: {}", modelName, t.getMessage());
                result = PullResult.failure(modelName, t.getMessage(), t);
            }
        }

        handle.markDone();

        if (result.isSuccess()) {
            modelApi.addToCache(modelName);
            log.info("모델 다운로드 완료: {} (소요시간: {}ms)", modelName, result.getTotalDurationMs());
        }

        callback.onComplete(result);
    }

    /**
     * 진행률 스트림을 끝까지 읽어 결과를 판정
     */
    private PullResult readStream(String modelName, StreamSession session, PreparedStream prepared,
                                  DefaultPullHandle handle, PullCallback callback, long startTime)
            throws java.io.IOException {

        AtomicReference<String> serverError = new AtomicReference<>();
        AtomicBoolean succeeded = new AtomicBoolean(false);

        NdjsonStreamReader.read(session.source(), http.objectMapper(), node -> {
            // Ollama는 실패를 HTTP 오류가 아니라 본문의 error 필드로 알리기도 한다
            if (node.has("error")) {
                serverError.set(node.get("error").asText(""));
                return true;
            }

            PullProgress progress = PullProgress.builder()
                    .status(node.path("status").asText(""))
                    .digest(node.path("digest").asText(null))
                    .completed(node.path("completed").asLong(0))
                    .total(node.path("total").asLong(0))
                    .build();

            handle.updateProgress(progress);

            // 소비자 콜백의 예외가 다운로드를 중단시키지 않도록 격리한다
            try {
                callback.onProgress(progress);
            } catch (Exception e) {
                log.warn("onProgress 콜백 처리 중 예외 발생 (무시됨): {}", e.getMessage());
            }

            if (progress.isSuccess()) {
                succeeded.set(true);
                return true;
            }
            return false;
        });

        if (serverError.get() != null) {
            log.error("모델 다운로드 에러: {}", serverError.get());
            return PullResult.failure(modelName, serverError.get());
        }
        if (succeeded.get()) {
            return PullResult.success(modelName, System.currentTimeMillis() - startTime);
        }
        if (prepared.isCancelled()) {
            return PullResult.cancelled(modelName);
        }
        return PullResult.failure(modelName, "다운로드가 완료되지 않았습니다");
    }
}
