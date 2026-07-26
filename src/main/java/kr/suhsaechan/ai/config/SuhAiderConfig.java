package kr.suhsaechan.ai.config;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

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
     * 모델 목록 자동 갱신 설정
     */
    private ModelRefresh modelRefresh = new ModelRefresh();

    /**
     * 임베딩 기본 설정
     */
    private Embedding embedding = new Embedding();

    /**
     * 비동기 실행 설정
     */
    private Async async = new Async();

    /**
     * 모델 다운로드 설정
     */
    private Pull pull = new Pull();

    /**
     * 기본 URL 설정 (후행 슬래시 제거)
     *
     * <p>경로를 문자열로 이어 붙이기 때문에 {@code https://host/}처럼 슬래시로 끝나면
     * 모든 요청이 {@code //api/tags}가 됩니다. 바인딩 시점에 정규화합니다.</p>
     *
     * @param baseUrl 설정된 기본 URL
     */
    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            this.baseUrl = null;
            return;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.baseUrl = trimmed;
    }

    /**
     * 설정 유효성 검증
     *
     * <p>잘못된 URL로 뜬 애플리케이션은 첫 호출 시점에야 실패합니다.
     * 시작 시점에 걸러 원인을 명확히 합니다.</p>
     *
     * @throws SuhAiderException baseUrl이 비어 있거나 http/https 형식이 아닌 경우
     */
    public void validate() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.BASE_URL_INVALID, "baseUrl이 비어있습니다");
        }

        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new SuhAiderException(SuhAiderErrorCode.BASE_URL_INVALID, baseUrl, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new SuhAiderException(SuhAiderErrorCode.BASE_URL_INVALID,
                    "http:// 또는 https:// 형식이어야 합니다: " + baseUrl);
        }
    }

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
     * 모델 목록 자동 갱신 설정 클래스
     */
    @Data
    public static class ModelRefresh {

        /**
         * 초기화 시 모델 목록 로드 여부
         * 기본값: true
         */
        private boolean loadOnStartup = true;

        /**
         * 스케줄링 활성화 여부
         * 기본값: false
         */
        private boolean schedulingEnabled = false;

        /**
         * 갱신 스케줄 Cron 표현식 (초 분 시 일 월 요일)
         * 기본값: "0 0 4 * * *" (매일 오전 4시)
         */
        private String cron = "0 0 4 * * *";

        /**
         * Cron 표현식 시간대
         * 기본값: Asia/Seoul
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
         * 기본값: embeddinggemma:latest
         */
        private String defaultModel = "embeddinggemma:latest";

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
             * 옵션: FIXED_SIZE, SENTENCE, PARAGRAPH
             */
            private String strategy = "FIXED_SIZE";

            /**
             * 청크당 최대 문자 수 (토큰 ≈ 문자/4 근사치)
             */
            private int chunkSize = 500;

            /**
             * 청크 간 오버랩 문자 수 (의미 손실 방지, 10~20% 권장)
             */
            private int overlapSize = 50;
        }
    }

    /**
     * 비동기 실행 설정 클래스
     */
    @Data
    public static class Async {

        /**
         * 비동기/스트리밍 전용 스레드풀 크기
         *
         * <p>모델 다운로드는 수십 분 이상 블로킹될 수 있어 공용 ForkJoinPool을 쓰면
         * 소비자 앱 전체의 병렬 처리가 굶습니다. 전용 풀로 격리합니다.</p>
         */
        private int poolSize = 4;
    }

    /**
     * 모델 다운로드 설정 클래스
     */
    @Data
    public static class Pull {

        /**
         * 동기 다운로드 최대 대기 시간
         *
         * <p>기본 60분. 무제한 대기는 장애 시 스레드가 영구히 묶입니다.</p>
         */
        private Duration timeout = Duration.ofMinutes(60);
    }
}
