package kr.suhsaechan.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.model.EmbeddingResponse;
import kr.suhsaechan.ai.model.FunctionRequest;
import kr.suhsaechan.ai.model.FunctionResponse;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.ModelInfo;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import kr.suhsaechan.ai.model.SuhAiderRequest;
import kr.suhsaechan.ai.model.SuhAiderResponse;
import kr.suhsaechan.ai.util.FormatUtils;
import kr.suhsaechan.ai.util.JsonResponseCleaner;
import kr.suhsaechan.ai.util.PromptEnhancer;
import kr.suhsaechan.ai.util.TextChunker;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * SUH-AIDER AI 서버와 통신하는 엔진
 * 핵심 기능:
 * 1. Health Check
 * 2. 모델 목록 조회
 * 3. Generate API (프롬프트 → 응답 생성)
 * 4. Generate Stream API (스트리밍 응답)
 * 5. Embedding API (텍스트 임베딩 생성)
 * 6. Chat API (대화형 세션 지원)
 */
@Service
@Slf4j
public class SuhAiderEngine {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SuhAiderConfig config;
    private final SuhAiderCustomizer customizer;

    /**
     * 캐싱된 사용 가능한 모델 목록
     */
    private List<ModelInfo> availableModels = new ArrayList<>();

    /**
     * 모델 목록 초기화 완료 여부
     */
    private volatile boolean modelsInitialized = false;

    /**
     * 생성자 주입 (Customizer는 선택적)
     */
    public SuhAiderEngine(
            @Qualifier("suhAiderHttpClient") OkHttpClient httpClient,
            @Qualifier("suhAiderObjectMapper") ObjectMapper objectMapper,
            SuhAiderConfig config,
            @Nullable @Autowired(required = false) SuhAiderCustomizer customizer
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config;
        this.customizer = customizer;
    }

    /**
     * 초기화 시점에 설정 검증 및 모델 목록 로드
     */
    @PostConstruct
    public void init() {
        log.info("SuhAiderEngine 초기화 - baseUrl: {}", config.getBaseUrl());

        // Security Header 설정 여부 확인 (선택적)
        if (!hasSecurityHeader()) {
            log.warn("Security Header가 설정되지 않았습니다. 인증이 필요한 서버에서는 401/403 오류가 발생할 수 있습니다.");
            log.warn("설정 방법: suh.aider.security.api-key 또는 환경변수 AI_API_KEY");
        } else {
            log.info("Security Header 설정됨 - header: {}", config.getSecurity().getHeaderName());
        }

        // 모델 목록 초기화 (설정에 따라)
        if (config.getModelRefresh().isLoadOnStartup()) {
            initializeModels();
        } else {
            log.info("모델 목록 초기화 건너뜀 (load-on-startup: false)");
        }

        // 스케줄링 설정 로그
        if (config.getModelRefresh().isSchedulingEnabled()) {
            log.info("모델 목록 자동 갱신 스케줄링 활성화 - cron: {}, timezone: {}",
                    config.getModelRefresh().getCron(),
                    config.getModelRefresh().getTimezone());
        }

        log.info("SuhAiderEngine 초기화 완료");
    }

    /**
     * 서버에서 모델 목록을 가져와서 캐싱
     * 초기화 시점 또는 스케줄링에 의해 호출됩니다.
     */
    private void initializeModels() {
        try {
            log.info("사용 가능한 모델 목록 로딩 중...");

            ModelListResponse response = getModels();

            if (response.getModels() != null && !response.getModels().isEmpty()) {
                this.availableModels = new ArrayList<>(response.getModels());
                this.modelsInitialized = true;

                log.info("모델 목록 로드 완료 - 총 {}개", availableModels.size());
                availableModels.forEach(model ->
                        log.debug("  - {}: {} ({})",
                                model.getName(),
                                model.getDetails() != null ?
                                        model.getDetails().getParameterSize() : "N/A",
                                FormatUtils.formatBytes(model.getSize())
                        )
                );
            } else {
                log.warn("서버에서 모델 목록을 가져왔으나 비어있습니다");
            }

        } catch (Exception e) {
            log.error("모델 목록 초기화 실패: {}", e.getMessage());
            log.warn("모델 검증 없이 진행합니다 (요청 시 서버에서 검증됨)");
        }
    }

    /**
     * 사용 가능한 모델 목록 반환 (캐시된 데이터)
     *
     * @return 모델 목록 (불변 리스트, 빈 리스트 가능)
     */
    public List<ModelInfo> getAvailableModels() {
        return Collections.unmodifiableList(availableModels);
    }

    /**
     * 특정 모델이 사용 가능한지 확인
     *
     * @param modelName 모델명
     * @return 사용 가능하면 true, 목록이 초기화되지 않았으면 항상 true (서버에서 검증)
     */
    public boolean isModelAvailable(String modelName) {
        if (!modelsInitialized) {
            log.debug("모델 목록이 초기화되지 않았습니다 - 서버에서 검증됩니다");
            return true;
        }

        return availableModels.stream()
                .anyMatch(model -> model.getName().equals(modelName));
    }

    /**
     * 모델 이름으로 상세 정보 가져오기
     *
     * @param modelName 모델명
     * @return 모델 정보 (없으면 empty)
     */
    public Optional<ModelInfo> getModelInfo(String modelName) {
        return availableModels.stream()
                .filter(model -> model.getName().equals(modelName))
                .findFirst();
    }

    /**
     * 모델 목록 수동 갱신
     * 스케줄러 또는 외부에서 호출하여 모델 목록을 갱신합니다.
     *
     * @return 갱신 성공 여부
     */
    public boolean refreshModels() {
        log.info("모델 목록 수동 갱신 시작");
        try {
            initializeModels();
            return modelsInitialized;
        } catch (Exception e) {
            log.error("모델 목록 갱신 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 모델 목록 초기화 완료 여부
     *
     * @return 초기화 완료되었으면 true
     */
    public boolean isModelsInitialized() {
        return modelsInitialized;
    }

    /**
     * 모델 삭제 (Ollama 서버에서 모델 제거)
     * DELETE /api/delete
     *
     * @param modelName 삭제할 모델명 (예: "llama3.2", "llama3.2:latest")
     * @return 삭제 성공 여부
     * @throws SuhAiderException 네트워크 오류, 모델 미존재 등
     */
    public boolean deleteModel(String modelName) {
        return deleteModel(modelName, true);
    }

    /**
     * 모델 삭제 (Ollama 서버에서 모델 제거)
     * DELETE /api/delete
     *
     * @param modelName   삭제할 모델명 (예: "llama3.2", "llama3.2:latest")
     * @param checkExists 삭제 전 모델 존재 여부 확인 (false면 바로 삭제 시도)
     * @return 삭제 성공 여부
     * @throws SuhAiderException 네트워크 오류, 모델 미존재 등
     */
    public boolean deleteModel(String modelName, boolean checkExists) {
        log.info("모델 삭제 시작 - 모델: {}, 존재 확인: {}", modelName, checkExists);

        // 파라미터 검증
        if (!StringUtils.hasText(modelName)) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }

        // 존재 확인 (선택적)
        if (checkExists && modelsInitialized) {
            boolean exists = availableModels.stream()
                    .anyMatch(model -> model.getName().equals(modelName));
            if (!exists) {
                log.warn("삭제 대상 모델이 캐시에 없음: {}", modelName);
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND,
                        "모델을 찾을 수 없습니다: " + modelName);
            }
        }

        String url = config.getBaseUrl() + "/api/delete";

        try {
            // 요청 Body 생성
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of("name", modelName)
            );

            Request request = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .delete(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("모델 삭제 실패 - HTTP {}: {}", response.code(), responseBody);

                    // 404는 MODEL_NOT_FOUND로 처리
                    if (response.code() == 404) {
                        throw new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND,
                                "모델을 찾을 수 없습니다: " + modelName);
                    }

                    throw new SuhAiderException(SuhAiderErrorCode.MODEL_DELETE_FAILED,
                            "HTTP " + response.code() + ": " + responseBody);
                }

                // 삭제 성공 - 캐시에서 해당 모델 제거
                removeModelFromCache(modelName);

                log.info("모델 삭제 완료: {}", modelName);
                return true;
            }

        } catch (SuhAiderException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            log.error("모델 삭제 타임아웃: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 생성 실패: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * 캐시에서 특정 모델 제거
     *
     * @param modelName 제거할 모델명
     */
    private void removeModelFromCache(String modelName) {
        if (!modelsInitialized) {
            return;
        }

        int sizeBefore = availableModels.size();
        availableModels.removeIf(model -> model.getName().equals(modelName));
        int sizeAfter = availableModels.size();

        if (sizeBefore != sizeAfter) {
            log.debug("캐시에서 모델 제거됨: {} ({}개 → {}개)", modelName, sizeBefore, sizeAfter);
        }
    }

    /**
     * 캐시에 모델 추가 (다운로드 완료 후 호출)
     *
     * @param modelName 추가할 모델명
     */
    private void addModelToCache(String modelName) {
        if (!modelsInitialized) {
            return;
        }

        boolean exists = availableModels.stream()
                .anyMatch(m -> m.getName().equals(modelName));
        if (!exists) {
            availableModels.add(ModelInfo.builder().name(modelName).build());
            log.debug("캐시에 모델 추가됨: {}", modelName);
        }
    }

    // ========== Model Pull API (Ollama /api/pull) ==========

    /**
     * 모델 다운로드 (동기 방식)
     * POST /api/pull
     *
     * <p>다운로드가 완료될 때까지 블로킹됩니다.
     * 대용량 모델의 경우 수십 분이 걸릴 수 있으므로,
     * 진행률이 필요하면 {@link #pullModelStream(String, PullCallback)}을 사용하세요.</p>
     *
     * <p>사용 예제:</p>
     * <pre>
     * boolean success = suhAiderEngine.pullModel("llama3.2");
     * if (success) {
     *     System.out.println("다운로드 완료!");
     * }
     * </pre>
     *
     * @param modelName 다운로드할 모델명 (예: "llama3.2", "llama3.2:70b")
     * @return 다운로드 성공 여부
     * @throws SuhAiderException 네트워크 오류, 모델 미존재 등
     */
    public boolean pullModel(String modelName) {
        return pullModel(modelName, false);
    }

    /**
     * 모델 다운로드 (동기 방식, insecure 옵션)
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기 (보안 위험, 비권장)
     * @return 다운로드 성공 여부
     * @throws SuhAiderException 네트워크 오류 등
     */
    public boolean pullModel(String modelName, boolean insecure) {
        log.info("모델 다운로드 시작 (동기) - 모델: {}, insecure: {}", modelName, insecure);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PullResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        pullModelStream(modelName, insecure, new PullCallback() {
            @Override
            public void onProgress(PullProgress progress) {
                // 동기 방식에서는 진행률 무시
                log.debug("다운로드 진행: {} - {}", modelName, progress.getFormattedProgress());
            }

            @Override
            public void onComplete(PullResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            // 무제한 대기 (대용량 모델 고려)
            latch.await();

            if (errorRef.get() != null) {
                Throwable error = errorRef.get();
                if (error instanceof SuhAiderException) {
                    throw (SuhAiderException) error;
                }
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED, error);
            }

            PullResult result = resultRef.get();
            if (result == null) {
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED, "결과가 없습니다");
            }

            if (result.isCancelled()) {
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_CANCELLED);
            }

            log.info("모델 다운로드 완료 - 모델: {}, 성공: {}, 소요시간: {}",
                    modelName, result.isSuccess(), result.getFormattedDuration());
            return result.isSuccess();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED, "다운로드가 인터럽트되었습니다", e);
        }
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능)
     * POST /api/pull (stream: true)
     *
     * <p>진행률을 실시간으로 받을 수 있으며, 언제든 취소할 수 있습니다.</p>
     *
     * <p>사용 예제:</p>
     * <pre>
     * PullHandle handle = suhAiderEngine.pullModelStream("llama3.2:70b", new PullCallback() {
     *     &#64;Override
     *     public void onProgress(PullProgress progress) {
     *         System.out.printf("다운로드 중: %s (%.1f%%)\n",
     *             progress.getStatus(), progress.getPercent());
     *     }
     *
     *     &#64;Override
     *     public void onComplete(PullResult result) {
     *         if (result.isSuccess()) {
     *             System.out.println("다운로드 완료! 소요시간: " + result.getFormattedDuration());
     *         }
     *     }
     *
     *     &#64;Override
     *     public void onError(Throwable error) {
     *         System.err.println("에러: " + error.getMessage());
     *     }
     * });
     *
     * // 나중에 취소
     * handle.cancel();
     * </pre>
     *
     * @param modelName 다운로드할 모델명
     * @param callback  진행률 콜백
     * @return PullHandle (취소 및 상태 확인용)
     */
    public PullHandle pullModelStream(String modelName, PullCallback callback) {
        return pullModelStream(modelName, false, callback);
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능, insecure 옵션)
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기
     * @param callback  진행률 콜백
     * @return PullHandle (취소 및 상태 확인용)
     */
    public PullHandle pullModelStream(String modelName, boolean insecure, PullCallback callback) {
        log.info("모델 다운로드 시작 (스트림) - 모델: {}, insecure: {}", modelName, insecure);

        // 파라미터 검증
        if (!StringUtils.hasText(modelName)) {
            callback.onError(new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다"));
            return createDummyHandle(modelName);
        }

        String url = config.getBaseUrl() + "/api/pull";
        long startTime = System.currentTimeMillis();

        try {
            // 요청 Body 생성
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "name", modelName,
                            "insecure", insecure,
                            "stream", true
                    )
            );

            Request request = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            // Pull 전용 클라이언트 (타임아웃 무제한)
            OkHttpClient pullClient = httpClient.newBuilder()
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build();

            Call call = pullClient.newCall(request);
            DefaultPullHandle handle = new DefaultPullHandle(modelName, call);

            // 별도 스레드에서 스트리밍 처리
            CompletableFuture.runAsync(() -> {
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        log.error("모델 다운로드 실패 - HTTP {}: {}", response.code(), responseBody);
                        handle.markDone();
                        callback.onError(new SuhAiderException(SuhAiderErrorCode.MODEL_PULL_FAILED,
                                "HTTP " + response.code() + ": " + responseBody));
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handle.markDone();
                        callback.onError(new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE));
                        return;
                    }

                    // 스트림 처리
                    BufferedSource source = body.source();
                    boolean success = false;

                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();

                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }

                        try {
                            JsonNode node = objectMapper.readTree(line);

                            // 에러 응답 확인
                            if (node.has("error")) {
                                String errorMsg = node.get("error").asText("");
                                log.error("모델 다운로드 에러: {}", errorMsg);
                                handle.markDone();
                                callback.onComplete(PullResult.failure(modelName, errorMsg));
                                return;
                            }

                            // 진행률 파싱
                            PullProgress progress = PullProgress.builder()
                                    .status(node.path("status").asText(""))
                                    .digest(node.path("digest").asText(null))
                                    .completed(node.path("completed").asLong(0))
                                    .total(node.path("total").asLong(0))
                                    .build();

                            handle.updateProgress(progress);
                            try {
                                callback.onProgress(progress);
                            } catch (Exception callbackException) {
                                log.warn("콜백 처리 중 예외 발생 (무시됨): {}", callbackException.getMessage());
                            }

                            // 성공 확인
                            if (progress.isSuccess()) {
                                success = true;
                                break;
                            }

                        } catch (JsonProcessingException e) {
                            log.warn("청크 파싱 실패 (건너뜀): {}", line);
                        }
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    handle.markDone();

                    if (success) {
                        // 캐시에 모델 추가
                        addModelToCache(modelName);
                        log.info("모델 다운로드 완료: {} (소요시간: {}ms)", modelName, duration);
                        callback.onComplete(PullResult.success(modelName, duration));
                    } else if (handle.isCancelled()) {
                        log.info("모델 다운로드 취소됨: {}", modelName);
                        callback.onComplete(PullResult.cancelled(modelName));
                    } else {
                        callback.onComplete(PullResult.failure(modelName, "다운로드가 완료되지 않았습니다"));
                    }

                } catch (IOException e) {
                    handle.markDone();
                    if (handle.isCancelled()) {
                        log.info("모델 다운로드 취소됨: {}", modelName);
                        callback.onComplete(PullResult.cancelled(modelName));
                    } else {
                        log.error("모델 다운로드 네트워크 오류: {}", e.getMessage());
                        callback.onError(new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e));
                    }
                }
            });

            return handle;

        } catch (JsonProcessingException e) {
            log.error("JSON 생성 실패: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e));
            return createDummyHandle(modelName);
        }
    }

    /**
     * 모델 다운로드 (비동기)
     * 백그라운드에서 다운로드하고 CompletableFuture로 결과 반환
     *
     * <p>사용 예제:</p>
     * <pre>
     * CompletableFuture&lt;PullResult&gt; future = suhAiderEngine.pullModelAsync("llama3.2");
     *
     * // 다른 작업 수행...
     *
     * // 결과 대기
     * PullResult result = future.get();
     * if (result.isSuccess()) {
     *     System.out.println("다운로드 완료!");
     * }
     * </pre>
     *
     * @param modelName 다운로드할 모델명
     * @return CompletableFuture&lt;PullResult&gt;
     */
    public CompletableFuture<PullResult> pullModelAsync(String modelName) {
        return pullModelAsync(modelName, null);
    }

    /**
     * 모델 다운로드 (비동기 + 진행률 리스너)
     *
     * <p>사용 예제:</p>
     * <pre>
     * CompletableFuture&lt;PullResult&gt; future = suhAiderEngine.pullModelAsync(
     *     "llama3.2",
     *     progress -&gt; System.out.printf("진행률: %.1f%%\n", progress.getPercent())
     * );
     *
     * PullResult result = future.get();
     * </pre>
     *
     * @param modelName        다운로드할 모델명
     * @param progressListener 진행률 리스너 (null 가능)
     * @return CompletableFuture&lt;PullResult&gt;
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

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        return future;
    }

    /**
     * 여러 모델을 병렬로 다운로드
     *
     * <p>사용 예제:</p>
     * <pre>
     * List&lt;PullHandle&gt; handles = suhAiderEngine.pullModelsParallel(
     *     List.of("llama3.2", "mistral", "codellama"),
     *     new PullCallback() {
     *         &#64;Override
     *         public void onProgress(PullProgress progress) {
     *             System.out.printf("진행: %s\n", progress.getFormattedProgress());
     *         }
     *         // ... onComplete, onError
     *     }
     * );
     *
     * // 모두 취소
     * handles.forEach(PullHandle::cancel);
     * </pre>
     *
     * @param modelNames 다운로드할 모델명 목록
     * @param callback   각 모델의 진행률/완료 콜백
     * @return 각 모델에 대한 PullHandle 목록
     */
    public List<PullHandle> pullModelsParallel(List<String> modelNames, PullCallback callback) {
        log.info("병렬 모델 다운로드 시작 - 모델 {}개: {}", modelNames.size(), modelNames);

        return modelNames.stream()
                .map(name -> pullModelStream(name, callback))
                .collect(Collectors.toList());
    }

    /**
     * 여러 모델 병렬 다운로드 (비동기)
     * 모든 다운로드가 완료되면 결과 목록 반환
     *
     * <p>사용 예제:</p>
     * <pre>
     * CompletableFuture&lt;List&lt;PullResult&gt;&gt; future =
     *     suhAiderEngine.pullModelsAsync(List.of("llama3.2", "mistral"));
     *
     * List&lt;PullResult&gt; results = future.get();
     * results.forEach(r -&gt; System.out.println(r.getModelName() + ": " + r.isSuccess()));
     * </pre>
     *
     * @param modelNames 다운로드할 모델명 목록
     * @return CompletableFuture&lt;List&lt;PullResult&gt;&gt;
     */
    public CompletableFuture<List<PullResult>> pullModelsAsync(List<String> modelNames) {
        log.info("비동기 병렬 모델 다운로드 시작 - 모델 {}개: {}", modelNames.size(), modelNames);

        List<CompletableFuture<PullResult>> futures = modelNames.stream()
                .map(this::pullModelAsync)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 에러 발생 시 반환할 더미 PullHandle 생성
     */
    private PullHandle createDummyHandle(String modelName) {
        return new PullHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public PullProgress getLatestProgress() {
                return null;
            }

            @Override
            public String getModelName() {
                return modelName;
            }
        };
    }

    /**
     * AI 서버 Health Check
     * Ollama 서버의 기본 엔드포인트(/)에 GET 요청을 보내 "Ollama is running" 문구 확인
     *
     * @return 서버가 정상 작동 중이면 true, 아니면 false
     */
    public boolean isHealthy() {
        log.debug("AI 서버 Health Check 시작: {}", config.getBaseUrl());

        try {
            Request request = addSecurityHeader(new Request.Builder())
                    .url(config.getBaseUrl())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Health Check 실패 - HTTP {}", response.code());
                    return false;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                boolean isHealthy = responseBody.toLowerCase().contains("ollama is running");

                log.info("Health Check 결과: {}", isHealthy ? "정상" : "비정상");
                return isHealthy;
            }

        } catch (IOException e) {
            log.error("Health Check 중 네트워크 오류: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 설치된 모델 목록 조회
     * GET /api/tags
     *
     * @return 모델 목록
     * @throws SuhAiderException 네트워크 오류 또는 파싱 오류 시
     */
    public ModelListResponse getModels() {
        log.debug("모델 목록 조회 시작");

        String url = config.getBaseUrl() + "/api/tags";

        try {
            Request request = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("모델 목록 조회 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpError(response.code(), responseBody);
                }

                if (!StringUtils.hasText(responseBody)) {
                    throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
                }

                ModelListResponse modelList = objectMapper.readValue(responseBody, ModelListResponse.class);
                log.info("모델 목록 조회 완료 - 모델 개수: {}",
                        modelList.getModels() != null ? modelList.getModels().size() : 0);

                return modelList;
            }

        } catch (SocketTimeoutException e) {
            log.error("모델 목록 조회 타임아웃: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * AI 텍스트 생성 (Generate API)
     * POST /api/generate
     *
     * @param request SuhAiderRequest (model, prompt, stream, responseSchema)
     * @return SuhAiderResponse (생성된 텍스트 포함)
     * @throws SuhAiderException 네트워크 오류 또는 파싱 오류 시
     */
    public SuhAiderResponse generate(SuhAiderRequest request) {
        log.debug("Generate 호출 - 모델: {}, 프롬프트 길이: {}, responseSchema: {}",
                request.getModel(),
                request.getPrompt() != null ? request.getPrompt().length() : 0,
                request.getResponseSchema() != null ? "있음" : "없음");

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "프롬프트가 비어있습니다");
        }

        // ✅ 1. 전역 기본 스키마 적용 (customizer가 있고, 요청에 스키마가 없으면)
        JsonSchema effectiveSchema = request.getResponseSchema();
        if (effectiveSchema == null && customizer != null) {
            effectiveSchema = customizer.getDefaultResponseSchema();
            log.debug("전역 기본 responseSchema 적용");
        }

        // ✅ 2. 프롬프트 자동 증강 (스키마가 있으면)
        String finalPrompt = request.getPrompt();
        if (effectiveSchema != null) {
            finalPrompt = PromptEnhancer.enhance(request.getPrompt(), effectiveSchema);
            log.debug("프롬프트 증강 완료 - 원본 {}자 → 증강 {}자",
                    request.getPrompt().length(), finalPrompt.length());
        }

        // ✅ 3. HTTP 요청 준비 (증강된 프롬프트 사용, responseSchema는 제외)
        SuhAiderRequest enhancedRequest = request.toBuilder()
                .prompt(finalPrompt)
                .responseSchema(null)  // Ollama API로 전송 안 함
                .build();

        String url = config.getBaseUrl() + "/api/generate";

        try {
            // JSON 페이로드 생성
            String jsonPayload = objectMapper.writeValueAsString(enhancedRequest);
            log.debug("Generate 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("Generate 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpError(response.code(), responseBody);
                }

                if (!StringUtils.hasText(responseBody)) {
                    throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
                }

                SuhAiderResponse suhAiderResponse = objectMapper.readValue(responseBody, SuhAiderResponse.class);

                // ✅ 4. JSON 응답 후처리 (스키마가 있었으면)
                if (effectiveSchema != null) {
                    String rawJsonResponse = suhAiderResponse.getResponse();
                    String cleanedJson = JsonResponseCleaner.clean(rawJsonResponse);
                    suhAiderResponse.setResponse(cleanedJson);

                    log.debug("JSON 응답 정제 완료 - 원본 {}자 → 정제 {}자",
                            rawJsonResponse != null ? rawJsonResponse.length() : 0,
                            cleanedJson.length());

                    // 검증
                    if (!JsonResponseCleaner.isValidJson(cleanedJson, objectMapper)) {
                        log.warn("AI가 유효하지 않은 JSON 반환 (원본 유지): {}",
                                cleanedJson.substring(0, Math.min(100, cleanedJson.length())));
                    } else {
                        log.debug("JSON 유효성 검증 성공");
                    }
                }

                log.info("Generate 완료 - 응답 길이: {}, 처리 시간: {}ms",
                        suhAiderResponse.getResponse() != null ? suhAiderResponse.getResponse().length() : 0,
                        suhAiderResponse.getTotalDuration() != null ? suhAiderResponse.getTotalDuration() / 1_000_000 : 0);

                return suhAiderResponse;
            }

        } catch (SocketTimeoutException e) {
            log.error("Generate 타임아웃: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * 간편 Generate 메서드
     * 모델명과 프롬프트만으로 바로 응답 텍스트를 받을 수 있습니다.
     *
     * @param model 모델명 (예: "llama2", "mistral")
     * @param prompt 프롬프트 텍스트
     * @return 생성된 응답 텍스트
     */
    public String generate(String model, String prompt) {
        SuhAiderRequest request = SuhAiderRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .build();

        SuhAiderResponse response = generate(request);
        return response.getResponse();
    }

    /**
     * AI 텍스트 생성 (스트리밍)
     * POST /api/generate (stream: true)
     *
     * <p>AI가 토큰을 생성할 때마다 실시간으로 콜백이 호출됩니다.
     * ChatGPT, Claude처럼 한 글자씩 표시되는 효과를 구현할 때 사용합니다.</p>
     *
     * <p><b>주의사항:</b> 스트리밍 모드에서는 {@code responseSchema}를 지원하지 않습니다.
     * 이유: 부분 텍스트만 받기 때문에 JSON 정제가 불가능하고, 실시간 표시 목적과 충돌합니다.
     * JSON 형식 응답이 필요하면 {@link #generate(SuhAiderRequest)} 메서드를 사용하세요.</p>
     *
     * <p>사용 예제:</p>
     * <pre>
     * suhAiderEngine.generateStream(request, new StreamCallback() {
     *     &#64;Override
     *     public void onNext(String chunk) {
     *         System.out.print(chunk);  // 실시간 출력
     *     }
     *
     *     &#64;Override
     *     public void onComplete() {
     *         System.out.println("\n완료!");
     *     }
     *
     *     &#64;Override
     *     public void onError(Throwable error) {
     *         System.err.println("에러: " + error.getMessage());
     *     }
     * });
     * </pre>
     *
     * @param request SuhAiderRequest (model, prompt 필수, responseSchema는 무시됨)
     * @param callback 스트리밍 콜백 (onNext, onComplete, onError)
     */
    public void generateStream(SuhAiderRequest request, StreamCallback callback) {
        log.debug("Generate Stream 호출 - 모델: {}, 프롬프트 길이: {}",
                request.getModel(),
                request.getPrompt() != null ? request.getPrompt().length() : 0);

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            callback.onError(new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다"));
            return;
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            callback.onError(new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "프롬프트가 비어있습니다"));
            return;
        }

        // ⚠️ 스트리밍 모드에서는 responseSchema를 지원하지 않습니다
        // 이유: 부분 텍스트만 받기 때문에 JSON 정제가 불가능하고, 실시간 표시 목적과 충돌합니다.
        if (request.getResponseSchema() != null) {
            log.warn("스트리밍 모드에서는 responseSchema가 무시됩니다. " +
                    "JSON 형식 응답이 필요하면 generate() 메서드를 사용하세요.");
        }
        if (customizer != null && customizer.getDefaultResponseSchema() != null) {
            log.warn("전역 기본 responseSchema가 설정되어 있지만, 스트리밍 모드에서는 무시됩니다.");
        }

        // 원본 프롬프트 그대로 사용 (증강하지 않음)
        String finalPrompt = request.getPrompt();

        // stream: true 강제 설정
        SuhAiderRequest streamRequest = request.toBuilder()
                .prompt(finalPrompt)
                .stream(true)
                .responseSchema(null)
                .build();

        String url = config.getBaseUrl() + "/api/generate";

        try {
            String jsonPayload = objectMapper.writeValueAsString(streamRequest);
            log.debug("Generate Stream 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.error("Generate Stream 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpErrorForCallback(response.code(), responseBody, callback);
                    return;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    callback.onError(new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE));
                    return;
                }

                // 스트림 처리
                BufferedSource source = responseBody.source();

                while (!source.exhausted()) {
                    String line = source.readUtf8Line();

                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(line);
                        String chunk = node.has("response") ? node.get("response").asText("") : "";

                        if (!chunk.isEmpty()) {
                            callback.onNext(chunk);
                        }

                        if (node.has("done") && node.get("done").asBoolean(false)) {
                            log.info("Generate Stream 완료");
                            callback.onComplete();
                            break;
                        }

                    } catch (JsonProcessingException e) {
                        log.warn("청크 파싱 실패 (건너뜀): {}", line);
                        // 파싱 실패해도 계속 진행
                    }
                }
            }

        } catch (SocketTimeoutException e) {
            log.error("Generate Stream 타임아웃: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e));
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e));
        } catch (IOException e) {
            log.error("Generate Stream 네트워크 오류: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e));
        }
    }

    /**
     * 간편 스트리밍 메서드
     * 모델명과 프롬프트만으로 스트리밍 응답을 받을 수 있습니다.
     *
     * @param model 모델명 (예: "llama2", "mistral")
     * @param prompt 프롬프트 텍스트
     * @param callback 스트리밍 콜백
     */
    public void generateStream(String model, String prompt, StreamCallback callback) {
        SuhAiderRequest request = SuhAiderRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(true)
                .build();

        generateStream(request, callback);
    }

    /**
     * 비동기 스트리밍 메서드
     * 백그라운드 스레드에서 스트리밍을 처리합니다.
     * Spring MVC의 SseEmitter와 함께 사용할 때 유용합니다.
     *
     * <p>사용 예제 (Spring MVC + SseEmitter):</p>
     * <pre>
     * &#64;GetMapping(value = "/ai/stream", produces = TEXT_EVENT_STREAM_VALUE)
     * public SseEmitter streamGenerate(&#64;RequestParam String prompt) {
     *     SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
     *
     *     suhAiderEngine.generateStreamAsync(request, new StreamCallback() {
     *         &#64;Override
     *         public void onNext(String chunk) {
     *             try {
     *                 emitter.send(SseEmitter.event().data(chunk));
     *             } catch (IOException e) {
     *                 emitter.completeWithError(e);
     *             }
     *         }
     *
     *         &#64;Override
     *         public void onComplete() {
     *             emitter.complete();
     *         }
     *
     *         &#64;Override
     *         public void onError(Throwable error) {
     *             emitter.completeWithError(error);
     *         }
     *     });
     *
     *     return emitter;
     * }
     * </pre>
     *
     * @param request SuhAiderRequest (model, prompt 필수)
     * @param callback 스트리밍 콜백
     * @return CompletableFuture (완료 시점 추적용)
     */
    public CompletableFuture<Void> generateStreamAsync(SuhAiderRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> generateStream(request, callback));
    }

    /**
     * 간편 비동기 스트리밍 메서드
     *
     * @param model 모델명 (예: "llama2", "mistral")
     * @param prompt 프롬프트 텍스트
     * @param callback 스트리밍 콜백
     * @return CompletableFuture (완료 시점 추적용)
     */
    public CompletableFuture<Void> generateStreamAsync(String model, String prompt, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> generateStream(model, prompt, callback));
    }

    // ========== Chat API (Ollama /api/chat) ==========

    /**
     * 대화형 AI 응답 생성 (Chat API)
     * POST /api/chat
     *
     * <p>이전 대화 기록을 포함하여 AI와 대화합니다.
     * messages 배열에 이전 대화를 포함하면 컨텍스트가 유지됩니다.</p>
     *
     * <p>사용 예제:</p>
     * <pre>
     * // 대화 기록 포함
     * ChatRequest request = ChatRequest.builder()
     *     .model("llama3")
     *     .messages(List.of(
     *         ChatMessage.system("너는 친절한 어시스턴트야"),
     *         ChatMessage.user("안녕?"),
     *         ChatMessage.assistant("안녕하세요!"),
     *         ChatMessage.user("방금 뭐라고 했어?")
     *     ))
     *     .build();
     *
     * ChatResponse response = engine.chat(request);
     * System.out.println(response.getContent());
     * </pre>
     *
     * @param request ChatRequest (model, messages 필수)
     * @return ChatResponse (AI 응답 메시지 포함)
     * @throws SuhAiderException 네트워크 오류 또는 파싱 오류 시
     */
    public ChatResponse chat(ChatRequest request) {
        log.debug("Chat 호출 - 모델: {}, 메시지 수: {}",
                request.getModel(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "메시지가 비어있습니다");
        }

        // responseSchema가 있으면 format으로 변환
        ChatRequest effectiveRequest = request;
        if (request.getResponseSchema() != null && request.getFormat() == null) {
            effectiveRequest = request.toBuilder()
                    .format(request.getResponseSchema().toFormatObject())
                    .responseSchema(null)
                    .build();
            log.debug("responseSchema를 format으로 변환");
        }

        String url = config.getBaseUrl() + "/api/chat";

        try {
            String jsonPayload = objectMapper.writeValueAsString(effectiveRequest);
            log.debug("Chat 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("Chat 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpError(response.code(), responseBody);
                }

                if (!StringUtils.hasText(responseBody)) {
                    throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
                }

                ChatResponse chatResponse = objectMapper.readValue(responseBody, ChatResponse.class);

                log.info("Chat 완료 - 응답 길이: {}, 처리 시간: {}ms",
                        chatResponse.getContent() != null ? chatResponse.getContent().length() : 0,
                        chatResponse.getTotalDurationMs() != null ? chatResponse.getTotalDurationMs() : 0);

                return chatResponse;
            }

        } catch (SocketTimeoutException e) {
            log.error("Chat 타임아웃: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("Chat 네트워크 오류: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * 간편 Chat 메서드
     * 모델명과 메시지 목록으로 바로 응답을 받습니다.
     *
     * @param model 모델명 (예: "llama3", "mistral")
     * @param messages 대화 메시지 목록
     * @return ChatResponse
     */
    public ChatResponse chat(String model, List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .stream(false)
                .build();

        return chat(request);
    }

    /**
     * 단일 메시지 Chat (간편)
     * 대화 기록 없이 단일 질문에 대한 응답을 받습니다.
     *
     * @param model 모델명
     * @param userMessage 사용자 메시지
     * @return AI 응답 텍스트
     */
    public String chat(String model, String userMessage) {
        ChatResponse response = chat(model, List.of(ChatMessage.user(userMessage)));
        return response.getContent();
    }

    /**
     * 시스템 프롬프트 포함 Chat (간편)
     *
     * @param model 모델명
     * @param systemPrompt 시스템 지시문
     * @param userMessage 사용자 메시지
     * @return AI 응답 텍스트
     */
    public String chat(String model, String systemPrompt, String userMessage) {
        ChatResponse response = chat(model, List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(userMessage)
        ));
        return response.getContent();
    }

    /**
     * 대화형 AI 응답 생성 (스트리밍)
     * POST /api/chat (stream: true)
     *
     * <p>AI가 토큰을 생성할 때마다 실시간으로 콜백이 호출됩니다.</p>
     *
     * <p>사용 예제:</p>
     * <pre>
     * engine.chatStream(request, new StreamCallback() {
     *     &#64;Override
     *     public void onNext(String chunk) {
     *         System.out.print(chunk);  // 실시간 출력
     *     }
     *
     *     &#64;Override
     *     public void onComplete() {
     *         System.out.println("\n완료!");
     *     }
     *
     *     &#64;Override
     *     public void onError(Throwable error) {
     *         System.err.println("에러: " + error.getMessage());
     *     }
     * });
     * </pre>
     *
     * @param request ChatRequest (model, messages 필수)
     * @param callback 스트리밍 콜백
     */
    public void chatStream(ChatRequest request, StreamCallback callback) {
        log.debug("Chat Stream 호출 - 모델: {}, 메시지 수: {}",
                request.getModel(),
                request.getMessages() != null ? request.getMessages().size() : 0);

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            callback.onError(new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다"));
            return;
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            callback.onError(new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "메시지가 비어있습니다"));
            return;
        }

        // stream: true 강제 설정
        ChatRequest streamRequest = request.toBuilder()
                .stream(true)
                .build();

        String url = config.getBaseUrl() + "/api/chat";

        try {
            String jsonPayload = objectMapper.writeValueAsString(streamRequest);
            log.debug("Chat Stream 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.error("Chat Stream 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpErrorForCallback(response.code(), responseBody, callback);
                    return;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    callback.onError(new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE));
                    return;
                }

                // 스트림 처리
                BufferedSource source = responseBody.source();

                while (!source.exhausted()) {
                    String line = source.readUtf8Line();

                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(line);

                        // Chat API는 message.content에 응답이 있음
                        if (node.has("message") && node.get("message").has("content")) {
                            String chunk = node.get("message").get("content").asText("");
                            if (!chunk.isEmpty()) {
                                callback.onNext(chunk);
                            }
                        }

                        if (node.has("done") && node.get("done").asBoolean(false)) {
                            log.info("Chat Stream 완료");
                            callback.onComplete();
                            break;
                        }

                    } catch (JsonProcessingException e) {
                        log.warn("청크 파싱 실패 (건너뜀): {}", line);
                    }
                }
            }

        } catch (SocketTimeoutException e) {
            log.error("Chat Stream 타임아웃: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e));
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e));
        } catch (IOException e) {
            log.error("Chat Stream 네트워크 오류: {}", e.getMessage());
            callback.onError(new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e));
        }
    }

    /**
     * 간편 Chat 스트리밍
     *
     * @param model 모델명
     * @param messages 대화 메시지 목록
     * @param callback 스트리밍 콜백
     */
    public void chatStream(String model, List<ChatMessage> messages, StreamCallback callback) {
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .stream(true)
                .build();

        chatStream(request, callback);
    }

    /**
     * 비동기 Chat 스트리밍
     * 백그라운드 스레드에서 스트리밍을 처리합니다.
     *
     * @param request ChatRequest
     * @param callback 스트리밍 콜백
     * @return CompletableFuture (완료 시점 추적용)
     */
    public CompletableFuture<Void> chatStreamAsync(ChatRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> chatStream(request, callback));
    }

    /**
     * 간편 비동기 Chat 스트리밍
     *
     * @param model 모델명
     * @param messages 대화 메시지 목록
     * @param callback 스트리밍 콜백
     * @return CompletableFuture
     */
    public CompletableFuture<Void> chatStreamAsync(String model, List<ChatMessage> messages, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> chatStream(model, messages, callback));
    }

    // ========== Embedding API (Ollama /api/embed) ==========

    /**
     * 단일 텍스트 임베딩 (간편)
     *
     * @param model 임베딩 모델명 (예: "nomic-embed-text")
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터
     */
    public List<Double> embed(String model, String text) {
        EmbeddingResponse response = embed(EmbeddingRequest.builder()
                .model(model)
                .input(text)
                .build());
        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE, "임베딩 결과가 비어있습니다");
        }
        return response.getEmbeddings().get(0);
    }

    /**
     * 배치 임베딩 (간편)
     * 여러 텍스트를 한 번에 임베딩
     *
     * @param model 임베딩 모델명
     * @param texts 임베딩할 텍스트 목록
     * @return 각 텍스트에 대응하는 임베딩 벡터 목록
     */
    public List<List<Double>> embed(String model, List<String> texts) {
        EmbeddingResponse response = embed(EmbeddingRequest.builder()
                .model(model)
                .input(texts)
                .build());
        if (response.getEmbeddings() == null) {
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE, "임베딩 결과가 비어있습니다");
        }
        return response.getEmbeddings();
    }

    /**
     * 임베딩 (상세 옵션)
     * POST /api/embed (Ollama 권장 엔드포인트)
     *
     * @param request EmbeddingRequest (model, input 필수)
     * @return EmbeddingResponse (임베딩 벡터 포함)
     * @throws SuhAiderException 네트워크 오류 또는 파싱 오류 시
     */
    public EmbeddingResponse embed(EmbeddingRequest request) {
        log.debug("Embed 호출 - model: {}", request.getModel());

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (request.getInput() == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "input이 비어있습니다");
        }

        // 기본값 적용 (config에서)
        EmbeddingRequest effectiveRequest = applyEmbeddingDefaults(request);

        String url = config.getBaseUrl() + "/api/embed";  // 권장 엔드포인트

        try {
            String jsonPayload = objectMapper.writeValueAsString(effectiveRequest);
            log.debug("Embed 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = addSecurityHeader(new Request.Builder())
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("Embed 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpError(response.code(), responseBody);
                }

                if (!StringUtils.hasText(responseBody)) {
                    throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
                }

                EmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, EmbeddingResponse.class);

                log.info("Embed 완료 - 벡터 개수: {}, 처리 시간: {}ms",
                        embeddingResponse.getEmbeddings() != null ? embeddingResponse.getEmbeddings().size() : 0,
                        embeddingResponse.getTotalDuration() != null ? embeddingResponse.getTotalDuration() / 1_000_000 : 0);

                return embeddingResponse;
            }

        } catch (SocketTimeoutException e) {
            log.error("Embed 타임아웃: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("Embed 네트워크 오류: {}", e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * 청킹 + 임베딩 (긴 텍스트용)
     * 설정된 청킹 전략에 따라 텍스트를 분할하고 각각 임베딩
     *
     * @param model 임베딩 모델명
     * @param text 임베딩할 텍스트 (긴 텍스트 가능)
     * @param chunkingConfig 청킹 설정
     * @return EmbeddingResponse (각 청크의 임베딩 벡터 포함)
     */
    public EmbeddingResponse embedWithChunking(String model, String text, ChunkingConfig chunkingConfig) {
        // 청킹 수행
        List<String> chunks = TextChunker.chunk(text, chunkingConfig);
        log.debug("청킹 완료 - 원본: {}자, {}개 청크 생성", text.length(), chunks.size());

        // 청크들을 배치로 임베딩
        return embed(EmbeddingRequest.builder()
                .model(model)
                .input(chunks)
                .build());
    }

    /**
     * 청킹 + 임베딩 (설정 기반)
     * application.yml의 suh.aider.embedding.chunking 설정 사용
     *
     * @param model 임베딩 모델명
     * @param text 임베딩할 텍스트
     * @return EmbeddingResponse
     */
    public EmbeddingResponse embedWithChunking(String model, String text) {
        ChunkingConfig chunkingConfig = buildChunkingConfigFromProperties();
        return embedWithChunking(model, text, chunkingConfig);
    }

    /**
     * 기본 모델로 청킹 + 임베딩
     * application.yml의 기본 모델과 청킹 설정 사용
     *
     * @param text 임베딩할 텍스트
     * @return EmbeddingResponse
     */
    public EmbeddingResponse embedWithChunking(String text) {
        String defaultModel = config.getEmbedding().getDefaultModel();
        return embedWithChunking(defaultModel, text);
    }

    /**
     * 임베딩 요청에 기본값 적용
     */
    private EmbeddingRequest applyEmbeddingDefaults(EmbeddingRequest request) {
        SuhAiderConfig.Embedding embeddingConfig = config.getEmbedding();

        return request.toBuilder()
                .truncate(request.getTruncate() != null ? request.getTruncate() : embeddingConfig.isTruncate())
                .keepAlive(request.getKeepAlive() != null ? request.getKeepAlive() : embeddingConfig.getKeepAlive())
                .dimensions(request.getDimensions() != null ? request.getDimensions() : embeddingConfig.getDimensions())
                .chunkingConfig(null)  // API로 전송 안 함
                .build();
    }

    /**
     * application.yml 설정에서 ChunkingConfig 빌드
     */
    private ChunkingConfig buildChunkingConfigFromProperties() {
        SuhAiderConfig.Embedding.Chunking props = config.getEmbedding().getChunking();

        ChunkingConfig.Strategy strategy;
        try {
            strategy = ChunkingConfig.Strategy.valueOf(props.getStrategy());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 청킹 전략: {}. 기본값 FIXED_SIZE 사용", props.getStrategy());
            strategy = ChunkingConfig.Strategy.FIXED_SIZE;
        }

        return ChunkingConfig.builder()
                .enabled(props.isEnabled())
                .strategy(strategy)
                .chunkSize(props.getChunkSize())
                .overlapSize(props.getOverlapSize())
                .build();
    }

    /**
     * HTTP 에러 코드에 따른 예외 처리
     */
    private void handleHttpError(int statusCode, String responseBody) {
        switch (statusCode) {
            case 401:
                throw new SuhAiderException(SuhAiderErrorCode.UNAUTHORIZED);
            case 403:
                throw new SuhAiderException(SuhAiderErrorCode.FORBIDDEN);
            case 404:
                throw new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND, responseBody);
            case 500:
            case 502:
            case 503:
                throw new SuhAiderException(SuhAiderErrorCode.SERVER_ERROR, responseBody);
            default:
                throw new SuhAiderException(SuhAiderErrorCode.INVALID_RESPONSE,
                        "HTTP " + statusCode + ": " + responseBody);
        }
    }

    /**
     * HTTP 에러를 콜백으로 전달 (스트리밍용)
     */
    private void handleHttpErrorForCallback(int statusCode, String responseBody, StreamCallback callback) {
        SuhAiderException exception;
        switch (statusCode) {
            case 401:
                exception = new SuhAiderException(SuhAiderErrorCode.UNAUTHORIZED);
                break;
            case 403:
                exception = new SuhAiderException(SuhAiderErrorCode.FORBIDDEN);
                break;
            case 404:
                exception = new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND, responseBody);
                break;
            case 500:
            case 502:
            case 503:
                exception = new SuhAiderException(SuhAiderErrorCode.SERVER_ERROR, responseBody);
                break;
            default:
                exception = new SuhAiderException(SuhAiderErrorCode.INVALID_RESPONSE,
                        "HTTP " + statusCode + ": " + responseBody);
        }
        callback.onError(exception);
    }

    /**
     * Security Header 설정 여부 확인
     *
     * @return API 키가 설정되어 있으면 true, 아니면 false
     */
    private boolean hasSecurityHeader() {
        return config.getSecurity() != null
                && StringUtils.hasText(config.getSecurity().getApiKey());
    }

    /**
     * Request Builder에 Security Header 추가 (있는 경우에만)
     *
     * @param builder OkHttp Request.Builder
     * @return 헤더가 추가된 Request.Builder
     */
    private Request.Builder addSecurityHeader(Request.Builder builder) {
        if (hasSecurityHeader()) {
            SuhAiderConfig.Security security = config.getSecurity();

            // {value}를 실제 API 키로 치환
            String headerValue = security.getHeaderValueFormat()
                    .replace("{value}", security.getApiKey());

            builder.addHeader(security.getHeaderName(), headerValue);

            log.debug("Security Header 추가 - {}: {}",
                    security.getHeaderName(),
                    maskSensitiveValue(headerValue));
        }
        return builder;
    }

    /**
     * 민감한 값 마스킹 (로그용)
     *
     * @param value 원본 값
     * @return 마스킹된 값 (앞 4자리만 표시)
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }

    // ========== Function Calling API ==========

    /**
     * Function Calling 수행
     * FunctionGemma 등 Function Calling 지원 모델로 사용자 의도를 분류합니다.
     *
     * <p>사용 예제:</p>
     * <pre>
     * // 1. 빌더 템플릿 정의 (한 번)
     * FunctionRequest.Builder myRouter = FunctionRequest.builder()
     *     .model("functiongemma")
     *     .systemPrompt("You are a strict router. Choose exactly ONE tool call.")
     *     .tool(FunctionTool.builder()
     *         .name("route_rag")
     *         .description("Use when user asks about config location")
     *         .parameter(FunctionParameter.required("query", "string", "Search query"))
     *         .build())
     *     .tool(FunctionTool.builder()
     *         .name("route_system")
     *         .description("Use for status/logs")
     *         .parameter(FunctionParameter.enumType("action", "Action", "get_status", "get_logs"))
     *         .build());
     *
     * // 2. 사용 (userText만 추가)
     * FunctionResponse response = engine.functionCall(
     *     myRouter.userText("SSE 설정 어디에 했지?").build()
     * );
     *
     * // 3. 결과 처리
     * if (response.hasToolCall()) {
     *     String toolName = response.getToolName();
     *     String query = response.getArgumentAsString("query");
     *     // 분기 처리...
     * }
     * </pre>
     *
     * @param request FunctionRequest (model, userText, systemPrompt, tools 필수)
     * @return FunctionResponse (toolName, arguments)
     * @throws SuhAiderException 네트워크 오류, 파라미터 오류 시
     */
    public FunctionResponse functionCall(FunctionRequest request) {
        log.debug("FunctionCall 호출 - 모델: {}, Tool 개수: {}",
                request.getModel(),
                request.getTools() != null ? request.getTools().size() : 0);

        // 1. 파라미터 검증
        validateFunctionRequest(request);

        // 2. FunctionRequest → ChatRequest 변환
        ChatRequest chatRequest = request.toChatRequest();

        // 3. Chat API 호출
        ChatResponse chatResponse = chat(chatRequest);

        // 4. ChatResponse → FunctionResponse 변환
        FunctionResponse functionResponse = FunctionResponse.fromChatResponse(chatResponse);

        log.info("FunctionCall 완료 - toolName: {}, hasToolCall: {}",
                functionResponse.getToolName(),
                functionResponse.isHasToolCall());

        return functionResponse;
    }

    /**
     * FunctionRequest 파라미터 검증
     *
     * @param request 검증할 요청
     * @throws SuhAiderException 필수 파라미터 누락 시
     */
    private void validateFunctionRequest(FunctionRequest request) {
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER,
                    "모델명이 비어있습니다");
        }
        if (!StringUtils.hasText(request.getUserText())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER,
                    "userText가 비어있습니다");
        }
        if (!StringUtils.hasText(request.getSystemPrompt())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER,
                    "systemPrompt가 비어있습니다");
        }
        if (request.getTools() == null || request.getTools().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER,
                    "tools가 비어있습니다. 최소 1개 이상의 FunctionTool을 정의해야 합니다");
        }
    }
}
