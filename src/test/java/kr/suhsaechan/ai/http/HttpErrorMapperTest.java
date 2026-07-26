package kr.suhsaechan.ai.http;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP 상태코드·IO 예외 매핑 테스트
 *
 * <p>v1.x는 이 매핑이 7곳에 복제돼 있어 경로마다 결과가 달랐습니다.</p>
 */
class HttpErrorMapperTest {

    @Test
    @DisplayName("인증 실패는 UNAUTHORIZED/FORBIDDEN으로 매핑된다")
    void mapsAuthFailures() {
        assertEquals(SuhAiderErrorCode.UNAUTHORIZED, HttpErrorMapper.fromStatus(401, "").getErrorCode());
        assertEquals(SuhAiderErrorCode.FORBIDDEN, HttpErrorMapper.fromStatus(403, "").getErrorCode());
    }

    @Test
    @DisplayName("404는 MODEL_NOT_FOUND로 매핑된다")
    void mapsNotFound() {
        assertEquals(SuhAiderErrorCode.MODEL_NOT_FOUND, HttpErrorMapper.fromStatus(404, "no such model").getErrorCode());
    }

    @Test
    @DisplayName("5xx는 SERVER_ERROR로 매핑된다")
    void mapsServerErrors() {
        for (int code : new int[]{500, 502, 503, 504}) {
            assertEquals(SuhAiderErrorCode.SERVER_ERROR, HttpErrorMapper.fromStatus(code, "").getErrorCode(),
                    "HTTP " + code);
        }
    }

    @Test
    @DisplayName("그 외 상태코드는 INVALID_RESPONSE로 매핑되고 상태코드가 메시지에 남는다")
    void mapsOtherStatuses() {
        SuhAiderException e = HttpErrorMapper.fromStatus(418, "teapot");

        assertEquals(SuhAiderErrorCode.INVALID_RESPONSE, e.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("418"));
    }

    @Test
    @DisplayName("작업별 실패 코드를 지정하면 인증/미존재를 제외하고 그 코드가 쓰인다")
    void appliesFallbackCode() {
        // 삭제 중 발생한 5xx는 MODEL_DELETE_FAILED로 전달된다
        assertEquals(SuhAiderErrorCode.MODEL_DELETE_FAILED,
                HttpErrorMapper.fromStatus(500, "", SuhAiderErrorCode.MODEL_DELETE_FAILED).getErrorCode());

        // 인증/미존재는 의미가 고정이라 fallback이 무시된다
        assertEquals(SuhAiderErrorCode.UNAUTHORIZED,
                HttpErrorMapper.fromStatus(401, "", SuhAiderErrorCode.MODEL_DELETE_FAILED).getErrorCode());
        assertEquals(SuhAiderErrorCode.MODEL_NOT_FOUND,
                HttpErrorMapper.fromStatus(404, "", SuhAiderErrorCode.MODEL_PULL_FAILED).getErrorCode());
    }

    @Test
    @DisplayName("타임아웃과 연결 실패를 구분해 매핑한다")
    void mapsIoExceptions() {
        assertEquals(SuhAiderErrorCode.TIMEOUT,
                HttpErrorMapper.fromIoException(new SocketTimeoutException("timeout")).getErrorCode());

        assertEquals(SuhAiderErrorCode.CONNECTION_FAILED,
                HttpErrorMapper.fromIoException(new ConnectException("refused")).getErrorCode());
        assertEquals(SuhAiderErrorCode.CONNECTION_FAILED,
                HttpErrorMapper.fromIoException(new UnknownHostException("nope")).getErrorCode());

        assertEquals(SuhAiderErrorCode.NETWORK_ERROR,
                HttpErrorMapper.fromIoException(new IOException("broken pipe")).getErrorCode());
    }
}
