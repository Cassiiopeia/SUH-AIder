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

    /**
     * 모델 목록 자동 갱신 설정
     */
    private ModelRefresh modelRefresh = new ModelRefresh();

    /**
     * 임베딩 기본 설정
     */
    private Embedding embedding = new Embedding();

    /**
     * 모델 목록 자동 갱신 설정 클래스
     */
    @Data
    public static class ModelRefresh {

        /**
         * 초기화 시 모델 목록 로드 여부
         * 기본값: true
         * true: Bean 초기화 시 서버에서 모델 목록을 로드합니다
         * false: 모델 목록을 로드하지 않습니다 (수동 호출 필요)
         */
        private boolean loadOnStartup = true;

        /**
         * 스케줄링 활성화 여부
         * 기본값: false
         * true: 지정된 cron 표현식에 따라 자동으로 모델 목록을 갱신합니다
         * false: 자동 갱신하지 않습니다 (수동 호출 또는 초기화 시에만 로드)
         */
        private boolean schedulingEnabled = false;

        /**
         * 갱신 스케줄 Cron 표현식
         * 기본값: "0 0 4 * * *" (매일 오전 4시)
         * 형식: 초 분 시 일 월 요일
         * 예시:
         *   - "0 0 4 * * *": 매일 오전 4시
         *   - "0 0 0 * * MON": 매주 월요일 자정
         */
        private String cron = "0 0 4 * * *";

        /**
         * Cron 표현식 시간대
         * 기본값: Asia/Seoul
         * 예시: UTC, America/New_York, Europe/London
         */
        private String timezone = "Asia/Seoul";
    }

    /**
     * 임베딩 설정 클래스
     */
    @Data
    public static class Embedding {

        /**
         * 기본 임베딩 모델
         * 기본값: nomic-embed-text
         */
        private String defaultModel = "nomic-embed-text";

        /**
         * 컨텍스트 초과 시 입력 자르기
         * 기본값: true
         */
        private boolean truncate = true;

        /**
         * 모델 메모리 유지 시간
         * 기본값: 5m
         */
        private String keepAlive = "5m";

        /**
         * 임베딩 차원 수 (null = 모델 기본값)
         */
        private Integer dimensions;

        /**
         * 청킹 설정
         */
        private Chunking chunking = new Chunking();

        /**
         * 청킹 설정 클래스
         */
        @Data
        public static class Chunking {

            /**
             * 청킹 활성화 여부
             * 기본값: false
             */
            private boolean enabled = false;

            /**
             * 청킹 전략
             * 기본값: FIXED_SIZE
             * 옵션: FIXED_SIZE, SENTENCE, PARAGRAPH
             */
            private String strategy = "FIXED_SIZE";

            /**
             * 청크당 최대 문자 수
             * 기본값: 500
             * // 토큰 ≈ 문자/4 근사치
             */
            private int chunkSize = 500;

            /**
             * 청크 간 오버랩 문자 수
             * 기본값: 50
             * // 의미 손실 방지 (10~20% 권장)
             */
            private int overlapSize = 50;
        }
    }
}
