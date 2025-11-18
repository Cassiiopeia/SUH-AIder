package kr.suhsaechan.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.JsonSchema;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * SUH-AIDER AI 서버와 통신하는 엔진
 * 핵심 기능:
 * 1. Health Check
 * 2. 모델 목록 조회
 * 3. Generate API (프롬프트 → 응답 생성)
 */
@Service
@Slf4j
public class SuhAiderEngine {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SuhAiderConfig config;
    private final SuhAiderCustomizer customizer;

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
     * 초기화 시점에 설정 검증
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

        log.info("SuhAiderEngine 초기화 완료");
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
