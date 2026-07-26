package kr.suhsaechan.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.api.ChatApi;
import kr.suhsaechan.ai.api.EmbeddingApi;
import kr.suhsaechan.ai.api.FunctionApi;
import kr.suhsaechan.ai.api.GenerateApi;
import kr.suhsaechan.ai.api.ModelApi;
import kr.suhsaechan.ai.api.PullApi;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * SUH-AIDER 자동 설정
 *
 * <p>HTTP 클라이언트부터 도메인 API, 파사드까지 모든 Bean을 여기 한 곳에서 등록합니다.
 * v1.x는 {@code SuhAiderEngine}에 {@code @Service}가 붙어 있으면서 동시에 이 클래스에서도
 * {@code @Bean}으로 등록해, 소비자가 라이브러리 패키지를 컴포넌트 스캔하면 등록 경로가
 * 두 개가 되는 문제가 있었습니다. v2.0에서는 {@code @Service}를 떼고 등록을 일원화했습니다.</p>
 *
 * <p>도메인 API도 각각 Bean으로 노출합니다. 소비자가 필요한 것만 주입받아 테스트에서
 * 개별 대체할 수 있습니다.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SuhAiderConfig.class)
@ConditionalOnProperty(prefix = "suh.aider", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SuhAiderClientConfig {

    private final SuhAiderConfig config;

    /**
     * SUH-AIDER 서버 통신용 OkHttpClient
     *
     * @return OkHttpClient
     */
    @Bean
    @ConditionalOnMissingBean(name = "suhAiderHttpClient")
    public OkHttpClient suhAiderHttpClient() {
        log.info("SuhAider OkHttpClient 초기화 - baseUrl: {}, connectTimeout: {}s, readTimeout: {}s",
                config.getBaseUrl(), config.getConnectTimeout(), config.getReadTimeout());

        return new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * JSON 직렬화/역직렬화용 ObjectMapper
     *
     * <p>Ollama가 새 필드를 추가해도 깨지지 않도록 미지의 필드는 무시합니다.</p>
     *
     * @return ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean(name = "suhAiderObjectMapper")
    public ObjectMapper suhAiderObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 비동기/스트리밍 전용 스레드풀
     *
     * @return SuhAiderExecutors
     */
    @Bean
    @ConditionalOnMissingBean(SuhAiderExecutors.class)
    public SuhAiderExecutors suhAiderExecutors() {
        return new SuhAiderExecutors(config.getAsync().getPoolSize());
    }

    /**
     * HTTP 통신 단일 지점
     *
     * @param httpClient   OkHttpClient
     * @param objectMapper ObjectMapper
     * @return SuhAiderHttpExecutor
     */
    @Bean
    @ConditionalOnMissingBean(SuhAiderHttpExecutor.class)
    public SuhAiderHttpExecutor suhAiderHttpExecutor(
            @Qualifier("suhAiderHttpClient") OkHttpClient httpClient,
            @Qualifier("suhAiderObjectMapper") ObjectMapper objectMapper) {
        return new SuhAiderHttpExecutor(httpClient, objectMapper, config);
    }

    /**
     * 모델 조회·캐시·삭제 API
     *
     * @param http HTTP 실행기
     * @return ModelApi
     */
    @Bean
    @ConditionalOnMissingBean(ModelApi.class)
    public ModelApi suhAiderModelApi(SuhAiderHttpExecutor http) {
        return new ModelApi(http, config);
    }

    /**
     * 텍스트 생성 API
     *
     * @param http       HTTP 실행기
     * @param customizer 전역 커스터마이저 (선택)
     * @return GenerateApi
     */
    @Bean
    @ConditionalOnMissingBean(GenerateApi.class)
    public GenerateApi suhAiderGenerateApi(SuhAiderHttpExecutor http,
                                           @Nullable @Autowired(required = false) SuhAiderCustomizer customizer) {
        return new GenerateApi(http, customizer);
    }

    /**
     * 대화 API
     *
     * @param http       HTTP 실행기
     * @param customizer 전역 커스터마이저 (선택)
     * @return ChatApi
     */
    @Bean
    @ConditionalOnMissingBean(ChatApi.class)
    public ChatApi suhAiderChatApi(SuhAiderHttpExecutor http,
                                   @Nullable @Autowired(required = false) SuhAiderCustomizer customizer) {
        return new ChatApi(http, customizer);
    }

    /**
     * 임베딩 API
     *
     * @param http HTTP 실행기
     * @return EmbeddingApi
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingApi.class)
    public EmbeddingApi suhAiderEmbeddingApi(SuhAiderHttpExecutor http) {
        return new EmbeddingApi(http, config);
    }

    /**
     * 모델 다운로드 API
     *
     * @param http      HTTP 실행기
     * @param modelApi  캐시 갱신 대상
     * @param executors 전용 스레드풀
     * @return PullApi
     */
    @Bean
    @ConditionalOnMissingBean(PullApi.class)
    public PullApi suhAiderPullApi(SuhAiderHttpExecutor http, ModelApi modelApi, SuhAiderExecutors executors) {
        return new PullApi(http, modelApi, executors, config);
    }

    /**
     * Function Calling API
     *
     * @param chatApi      내부적으로 사용하는 Chat API
     * @param objectMapper arguments 파싱용 매퍼
     * @return FunctionApi
     */
    @Bean
    @ConditionalOnMissingBean(FunctionApi.class)
    public FunctionApi suhAiderFunctionApi(ChatApi chatApi,
                                           @Qualifier("suhAiderObjectMapper") ObjectMapper objectMapper) {
        return new FunctionApi(chatApi, objectMapper);
    }

    /**
     * 통합 진입점 파사드
     *
     * @param modelApi     모델 API
     * @param generateApi  생성 API
     * @param chatApi      대화 API
     * @param embeddingApi 임베딩 API
     * @param pullApi      다운로드 API
     * @param functionApi  Function Calling API
     * @param executors    전용 스레드풀
     * @return SuhAiderEngine
     */
    @Bean
    @ConditionalOnMissingBean(SuhAiderEngine.class)
    public SuhAiderEngine suhAiderEngine(ModelApi modelApi,
                                         GenerateApi generateApi,
                                         ChatApi chatApi,
                                         EmbeddingApi embeddingApi,
                                         PullApi pullApi,
                                         FunctionApi functionApi,
                                         SuhAiderExecutors executors) {
        log.info("SuhAiderEngine Bean 생성");
        return new SuhAiderEngine(modelApi, generateApi, chatApi, embeddingApi,
                pullApi, functionApi, config, executors);
    }
}
