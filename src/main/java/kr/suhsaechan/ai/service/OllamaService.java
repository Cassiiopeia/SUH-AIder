package kr.suhsaechan.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.OllamaProperties;
import kr.suhsaechan.ai.exception.OllamaErrorCode;
import kr.suhsaechan.ai.exception.OllamaException;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.model.OllamaRequest;
import kr.suhsaechan.ai.model.OllamaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Ollama AI 서버와 통신하는 서비스
 * 핵심 기능:
 * 1. Health Check
 * 2. 모델 목록 조회
 * 3. Generate API (프롬프트 → 응답 생성)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    @Qualifier("ollamaHttpClient")
    private final OkHttpClient httpClient;

    @Qualifier("ollamaObjectMapper")
    private final ObjectMapper objectMapper;

    private final OllamaProperties properties;

    /**
     * 초기화 시점에 필수 설정 검증
     */
    @PostConstruct
    public void init() {
        log.info("OllamaService 초기화 - baseUrl: {}", properties.getBaseUrl());

        // API 키 필수 검증
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.error("API 키가 설정되지 않았습니다. suh.ai.api-key를 환경변수 또는 application.yml에 설정해주세요.");
            throw new OllamaException(OllamaErrorCode.API_KEY_MISSING);
        }

        log.info("OllamaService 초기화 완료");
    }

    /**
     * AI 서버 Health Check
     * Ollama 서버의 기본 엔드포인트(/)에 GET 요청을 보내 "Ollama is running" 문구 확인
     *
     * @return 서버가 정상 작동 중이면 true, 아니면 false
     */
    public boolean isHealthy() {
        log.debug("AI 서버 Health Check 시작: {}", properties.getBaseUrl());

        try {
            Request request = new Request.Builder()
                    .url(properties.getBaseUrl())
                    .addHeader("X-API-Key", properties.getApiKey())
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
     * @throws OllamaException 네트워크 오류 또는 파싱 오류 시
     */
    public ModelListResponse getModels() {
        log.debug("모델 목록 조회 시작");

        String url = properties.getBaseUrl() + "/api/tags";

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-API-Key", properties.getApiKey())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("모델 목록 조회 실패 - HTTP {}: {}", response.code(), responseBody);
                    handleHttpError(response.code(), responseBody);
                }

                if (!StringUtils.hasText(responseBody)) {
                    throw new OllamaException(OllamaErrorCode.EMPTY_RESPONSE);
                }

                ModelListResponse modelList = objectMapper.readValue(responseBody, ModelListResponse.class);
                log.info("모델 목록 조회 완료 - 모델 개수: {}",
                        modelList.getModels() != null ? modelList.getModels().size() : 0);

                return modelList;
            }

        } catch (SocketTimeoutException e) {
            log.error("모델 목록 조회 타임아웃: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.NETWORK_ERROR, e);
        }
    }

    /**
     * AI 텍스트 생성 (Generate API)
     * POST /api/generate
     *
     * @param request OllamaRequest (model, prompt, stream)
     * @return OllamaResponse (생성된 텍스트 포함)
     * @throws OllamaException 네트워크 오류 또는 파싱 오류 시
     */
    public OllamaResponse generate(OllamaRequest request) {
        log.debug("Generate 호출 - 모델: {}, 프롬프트 길이: {}",
                request.getModel(),
                request.getPrompt() != null ? request.getPrompt().length() : 0);

        // 파라미터 검증
        if (!StringUtils.hasText(request.getModel())) {
            throw new OllamaException(OllamaErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new OllamaException(OllamaErrorCode.INVALID_PARAMETER, "프롬프트가 비어있습니다");
        }

        String url = properties.getBaseUrl() + "/api/generate";

        try {
            // JSON 페이로드 생성
            String jsonPayload = objectMapper.writeValueAsString(request);
            log.debug("Generate 요청 페이로드: {}", jsonPayload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .addHeader("X-API-Key", properties.getApiKey())
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
                    throw new OllamaException(OllamaErrorCode.EMPTY_RESPONSE);
                }

                OllamaResponse ollamaResponse = objectMapper.readValue(responseBody, OllamaResponse.class);
                log.info("Generate 완료 - 응답 길이: {}, 처리 시간: {}ms",
                        ollamaResponse.getResponse() != null ? ollamaResponse.getResponse().length() : 0,
                        ollamaResponse.getTotalDuration() != null ? ollamaResponse.getTotalDuration() / 1_000_000 : 0);

                return ollamaResponse;
            }

        } catch (SocketTimeoutException e) {
            log.error("Generate 타임아웃: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.READ_TIMEOUT, e);
        } catch (JsonProcessingException e) {
            log.error("JSON 처리 실패: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.JSON_PARSE_ERROR, e);
        } catch (IOException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new OllamaException(OllamaErrorCode.NETWORK_ERROR, e);
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
        OllamaRequest request = OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .build();

        OllamaResponse response = generate(request);
        return response.getResponse();
    }

    /**
     * HTTP 에러 코드에 따른 예외 처리
     */
    private void handleHttpError(int statusCode, String responseBody) {
        switch (statusCode) {
            case 401:
                throw new OllamaException(OllamaErrorCode.UNAUTHORIZED);
            case 403:
                throw new OllamaException(OllamaErrorCode.FORBIDDEN);
            case 404:
                throw new OllamaException(OllamaErrorCode.MODEL_NOT_FOUND, responseBody);
            case 500:
            case 502:
            case 503:
                throw new OllamaException(OllamaErrorCode.SERVER_ERROR, responseBody);
            default:
                throw new OllamaException(OllamaErrorCode.INVALID_RESPONSE,
                        "HTTP " + statusCode + ": " + responseBody);
        }
    }
}
