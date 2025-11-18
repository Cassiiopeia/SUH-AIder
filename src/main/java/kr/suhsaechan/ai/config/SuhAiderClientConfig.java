package kr.suhsaechan.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.concurrent.TimeUnit;

/**
 * SUH-AIDER 클라이언트 설정
 * OkHttpClient와 ObjectMapper Bean을 생성합니다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SuhAiderConfig.class)
@ConditionalOnProperty(prefix = "suh.aider", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SuhAiderClientConfig {

    private final SuhAiderConfig config;

    /**
     * SUH-AIDER 서버 통신용 OkHttpClient Bean 생성
     * - 타임아웃 설정 (config 기반)
     * - Connection Pool 설정 (성능 최적화)
     * - 리다이렉트 자동 처리
     */
    @Bean
    @ConditionalOnMissingBean(name = "suhAiderHttpClient")
    public OkHttpClient suhAiderHttpClient() {
        log.info("SuhAider OkHttpClient 초기화 - baseUrl: {}, connectTimeout: {}s, readTimeout: {}s",
                config.getBaseUrl(),
                config.getConnectTimeout(),
                config.getReadTimeout());

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
     * JSON 직렬화/역직렬화용 ObjectMapper Bean 생성
     * - 알 수 없는 필드 무시 (하위 호환성)
     */
    @Bean
    @ConditionalOnMissingBean(name = "suhAiderObjectMapper")
    public ObjectMapper suhAiderObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * SuhAiderEngine Bean 생성 (Customizer 주입 지원)
     *
     * 사용자가 SuhAiderCustomizer를 @Bean으로 등록하면 자동 주입됩니다.
     */
    @Bean
    @ConditionalOnMissingBean(SuhAiderEngine.class)
    public SuhAiderEngine suhAiderEngine(
            @Qualifier("suhAiderHttpClient") OkHttpClient httpClient,
            @Qualifier("suhAiderObjectMapper") ObjectMapper objectMapper,
            @Autowired(required = false) SuhAiderCustomizer customizer
    ) {
        log.info("SuhAiderEngine Bean 생성 - customizer: {}", customizer != null ? "있음" : "없음");
        return new SuhAiderEngine(httpClient, objectMapper, config, customizer);
    }
}
