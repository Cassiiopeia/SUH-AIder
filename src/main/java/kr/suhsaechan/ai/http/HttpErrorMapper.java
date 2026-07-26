package kr.suhsaechan.ai.http;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * HTTP 상태코드와 저수준 IO 예외를 {@link SuhAiderException}으로 변환하는 단일 지점
 *
 * <p>v1.x에서는 이 매핑이 엔진 곳곳에 switch/catch 블록으로 7번 복제돼 있었고,
 * 스트리밍 경로와 동기 경로가 서로 다른 사본을 써서 조금씩 어긋났습니다.</p>
 */
public final class HttpErrorMapper {

    private HttpErrorMapper() {
        // 유틸리티 클래스
    }

    /**
     * HTTP 실패 응답을 예외로 변환 (기본 매핑)
     *
     * @param statusCode   HTTP 상태 코드
     * @param responseBody 응답 본문 (null 가능)
     * @return 변환된 예외 (호출자가 throw 하거나 콜백으로 전달)
     */
    public static SuhAiderException fromStatus(int statusCode, String responseBody) {
        return fromStatus(statusCode, responseBody, null);
    }

    /**
     * HTTP 실패 응답을 예외로 변환 (작업별 실패 코드 지정)
     *
     * <p>인증(401/403)과 미존재(404)는 의미가 고정이라 {@code fallback}을 무시합니다.
     * 그 외 상태코드는 호출한 작업의 성격을 반영하도록 {@code fallback}을 사용합니다.
     * 예를 들어 모델 삭제 중 발생한 5xx는 {@code MODEL_DELETE_FAILED}로 전달됩니다.</p>
     *
     * @param statusCode   HTTP 상태 코드
     * @param responseBody 응답 본문 (null 가능)
     * @param fallback     작업별 실패 코드 (null이면 상태코드 기본 매핑)
     * @return 변환된 예외
     */
    public static SuhAiderException fromStatus(int statusCode, String responseBody, SuhAiderErrorCode fallback) {
        String detail = responseBody != null ? responseBody : "";

        switch (statusCode) {
            case 401:
                return new SuhAiderException(SuhAiderErrorCode.UNAUTHORIZED);
            case 403:
                return new SuhAiderException(SuhAiderErrorCode.FORBIDDEN);
            case 404:
                return new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND, detail);
            default:
                break;
        }

        if (fallback != null) {
            return new SuhAiderException(fallback, "HTTP " + statusCode + ": " + detail);
        }

        switch (statusCode) {
            case 500:
            case 502:
            case 503:
            case 504:
                return new SuhAiderException(SuhAiderErrorCode.SERVER_ERROR, detail);
            default:
                return new SuhAiderException(SuhAiderErrorCode.INVALID_RESPONSE,
                        "HTTP " + statusCode + ": " + detail);
        }
    }

    /**
     * IO 예외를 도메인 예외로 변환
     *
     * <p>OkHttp는 연결/읽기 타임아웃을 모두 {@code SocketTimeoutException}으로 던지므로
     * 둘을 구분하지 않고 {@code TIMEOUT}으로 매핑합니다. 메시지 문자열로 추측해
     * 잘못 분류하는 것보다 하나로 합치는 편이 정확합니다.</p>
     *
     * @param e 발생한 IO 예외
     * @return 변환된 예외
     */
    public static SuhAiderException fromIoException(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return new SuhAiderException(SuhAiderErrorCode.TIMEOUT, e);
        }
        if (e instanceof ConnectException || e instanceof UnknownHostException) {
            return new SuhAiderException(SuhAiderErrorCode.CONNECTION_FAILED, e);
        }
        return new SuhAiderException(SuhAiderErrorCode.NETWORK_ERROR, e);
    }
}
