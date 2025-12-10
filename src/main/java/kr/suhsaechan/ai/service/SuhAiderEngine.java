package kr.suhsaechan.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.ModelInfo;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.model.SuhAiderRequest;
import kr.suhsaechan.ai.model.SuhAiderResponse;
import kr.suhsaechan.ai.util.JsonResponseCleaner;
import kr.suhsaechan.ai.util.PromptEnhancer;
import lombok.extern.slf4j.Slf4j;
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

/**
 * SUH-AIDER AI 서버와 통신하는 엔진
 * 핵심 기능:
 * 1. Health Check
 * 2. 모델 목록 조회
 * 3. Generate API (프롬프트 → 응답 생성)
 * 4. Generate Stream API (스트리밍 응답)
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
                                formatSize(model.getSize())
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
     * 파일 크기 포맷팅 (사람이 읽기 쉽게)
     *
     * @param bytes 바이트 크기
     * @return 포맷된 문자열 (예: "7.03 GB")
     */
    private String formatSize(Long bytes) {
        if (bytes == null) return "N/A";

        if (bytes < 1024) return bytes + " B";

        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);

        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);

        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
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
}
