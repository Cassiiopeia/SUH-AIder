package kr.suhsaechan.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.service.OllamaService;
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
 * Ollama 클라이언트 설정
 * OkHttpClient와 ObjectMapper Bean을 생성합니다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
@ConditionalOnProperty(prefix = "suh.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OllamaClientConfig {

    private final OllamaProperties properties;

    /**
     * Ollama 서버 통신용 OkHttpClient Bean 생성
     * - 타임아웃 설정 (properties 기반)
     * - Connection Pool 설정 (성능 최적화)
     * - 리다이렉트 자동 처리
     */
    @Bean
    @ConditionalOnMissingBean(name = "ollamaHttpClient")
    public OkHttpClient ollamaHttpClient() {
        log.info("Ollama OkHttpClient 초기화 - baseUrl: {}, connectTimeout: {}s, readTimeout: {}s",
                properties.getBaseUrl(),
                properties.getConnectTimeout(),
                properties.getReadTimeout());

        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeout(), TimeUnit.SECONDS)
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
    @ConditionalOnMissingBean(name = "ollamaObjectMapper")
    public ObjectMapper ollamaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * OllamaService Bean 생성 (Customizer 주입 지원)
     *
     * 사용자가 OllamaServiceCustomizer를 @Bean으로 등록하면 자동 주입됩니다.
     */
    @Bean
    @ConditionalOnMissingBean(OllamaService.class)
    public OllamaService ollamaService(
            @Qualifier("ollamaHttpClient") OkHttpClient httpClient,
            @Qualifier("ollamaObjectMapper") ObjectMapper objectMapper,
            @Autowired(required = false) OllamaServiceCustomizer customizer
    ) {
        log.info("OllamaService Bean 생성 - customizer: {}", customizer != null ? "있음" : "없음");
        return new OllamaService(httpClient, objectMapper, properties, customizer);
    }
}
