package kr.suhsaechan.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SUH-AIDER AI 서버 연동을 위한 설정 프로퍼티
 */
@Data
@ConfigurationProperties(prefix = "suh.aider")
public class SuhAiderConfig {

    /**
     * AI 서버 기본 URL
     * 기본값: https://ai.suhsaechan.kr
     */
    private String baseUrl = "https://ai.suhsaechan.kr";

    /**
     * Security Header 설정 (선택적)
     * 설정하지 않으면 인증 헤더를 추가하지 않습니다.
     */
    private Security security = new Security();

    /**
     * HTTP 연결 타임아웃 (초)
     * 기본값: 30초
     */
    private int connectTimeout = 30;

    /**
     * HTTP 읽기 타임아웃 (초)
     * AI 응답 생성 시간을 고려하여 긴 시간 설정
     * 기본값: 120초
     */
    private int readTimeout = 120;

    /**
     * HTTP 쓰기 타임아웃 (초)
     * 기본값: 30초
     */
    private int writeTimeout = 30;

    /**
     * Auto-Configuration 활성화 여부
     * 기본값: true
     */
    private boolean enabled = true;

    /**
     * Security Header 설정 클래스
     */
    @Data
    public static class Security {

        /**
         * HTTP 헤더 이름
         * 기본값: X-API-Key
         * 예시: Authorization, X-Custom-Auth 등
         */
        private String headerName = "X-API-Key";

        /**
         * 헤더 값 포맷
         * {value}는 apiKey 값으로 치환됩니다.
         *
         * 기본값: "{value}" (값 그대로)
         * Bearer 토큰: "Bearer {value}"
         * 커스텀: "CustomScheme {value}"
         */
        private String headerValueFormat = "{value}";

        /**
         * API 인증 키 (선택적)
         * 설정하지 않으면 헤더를 추가하지 않습니다.
         */
        private String apiKey;
    }
}
