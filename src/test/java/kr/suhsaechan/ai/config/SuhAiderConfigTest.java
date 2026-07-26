package kr.suhsaechan.ai.config;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 설정 정규화·검증 테스트
 */
class SuhAiderConfigTest {

    @Test
    @DisplayName("baseUrl 후행 슬래시를 제거한다")
    void normalizesTrailingSlash() {
        SuhAiderConfig config = new SuhAiderConfig();

        config.setBaseUrl("https://ai.suhsaechan.kr/");
        assertEquals("https://ai.suhsaechan.kr", config.getBaseUrl());

        // 슬래시가 여러 개여도 모두 제거한다
        config.setBaseUrl("https://ai.suhsaechan.kr///");
        assertEquals("https://ai.suhsaechan.kr", config.getBaseUrl());
    }

    @Test
    @DisplayName("baseUrl 앞뒤 공백을 제거한다")
    void trimsWhitespace() {
        SuhAiderConfig config = new SuhAiderConfig();

        config.setBaseUrl("  http://localhost:11434  ");
        assertEquals("http://localhost:11434", config.getBaseUrl());
    }

    @Test
    @DisplayName("정상 baseUrl은 검증을 통과한다")
    void acceptsValidUrl() {
        SuhAiderConfig config = new SuhAiderConfig();

        config.setBaseUrl("http://localhost:11434");
        assertDoesNotThrow(config::validate);

        config.setBaseUrl("https://ai.suhsaechan.kr");
        assertDoesNotThrow(config::validate);
    }

    @Test
    @DisplayName("비어있는 baseUrl은 BASE_URL_INVALID로 실패한다")
    void rejectsEmptyUrl() {
        SuhAiderConfig config = new SuhAiderConfig();
        config.setBaseUrl("");

        SuhAiderException e = assertThrows(SuhAiderException.class, config::validate);
        assertEquals(SuhAiderErrorCode.BASE_URL_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("스킴이 없는 baseUrl은 BASE_URL_INVALID로 실패한다")
    void rejectsUrlWithoutScheme() {
        SuhAiderConfig config = new SuhAiderConfig();
        config.setBaseUrl("ai.suhsaechan.kr");

        SuhAiderException e = assertThrows(SuhAiderException.class, config::validate);
        assertEquals(SuhAiderErrorCode.BASE_URL_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("http/https가 아닌 스킴은 거부한다")
    void rejectsNonHttpScheme() {
        SuhAiderConfig config = new SuhAiderConfig();
        config.setBaseUrl("ftp://ai.suhsaechan.kr");

        SuhAiderException e = assertThrows(SuhAiderException.class, config::validate);
        assertEquals(SuhAiderErrorCode.BASE_URL_INVALID, e.getErrorCode());
    }
}
