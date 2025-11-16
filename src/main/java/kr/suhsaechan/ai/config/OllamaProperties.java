package kr.suhsaechan.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama AI 서버 연동을 위한 설정 프로퍼티
 */
@Data
@ConfigurationProperties(prefix = "suh.ai")
public class OllamaProperties {

    /**
     * Ollama 서버 기본 URL
     * 기본값: https://ai.suhsaechan.kr
     */
    private String baseUrl = "https://ai.suhsaechan.kr";

    /**
     * API 인증 키 (X-API-Key 헤더에 사용)
     * 환경변수 또는 application.yml에서 설정
     */
    private String apiKey;

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
}
