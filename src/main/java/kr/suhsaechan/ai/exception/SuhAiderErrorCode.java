package kr.suhsaechan.ai.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SUH-AIDER 서버 통신 중 발생 가능한 에러 코드
 *
 * <p>v2.0에서 실제로 도달 불가능하던 코드를 제거하고 타임아웃 분류를 단순화했습니다.
 * OkHttp는 연결/읽기 타임아웃을 모두 {@code SocketTimeoutException}으로 던지기 때문에
 * 둘을 신뢰성 있게 구분할 수 없어 {@link #TIMEOUT} 하나로 합쳤습니다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SuhAiderErrorCode {

    // 설정 관련 에러
    BASE_URL_INVALID("서버 URL이 올바르지 않습니다."),

    // 네트워크 에러
    NETWORK_ERROR("네트워크 연결 중 오류가 발생했습니다."),
    CONNECTION_FAILED("서버에 연결할 수 없습니다."),
    TIMEOUT("서버 응답 대기 시간이 초과되었습니다."),

    // API 응답 에러
    INVALID_RESPONSE("서버 응답 형식이 올바르지 않습니다."),
    JSON_PARSE_ERROR("JSON 파싱 중 오류가 발생했습니다."),
    EMPTY_RESPONSE("서버로부터 빈 응답을 받았습니다."),

    // 비즈니스 로직 에러
    MODEL_NOT_FOUND("요청한 모델을 찾을 수 없습니다."),
    INVALID_PARAMETER("잘못된 파라미터입니다."),
    SERVER_ERROR("AI 서버에서 오류가 발생했습니다."),

    // 인증 에러
    UNAUTHORIZED("API 키가 올바르지 않습니다."),
    FORBIDDEN("접근 권한이 없습니다."),

    // 모델 관리 에러
    MODEL_DELETE_FAILED("모델 삭제에 실패했습니다."),
    MODEL_PULL_FAILED("모델 다운로드에 실패했습니다."),
    MODEL_PULL_CANCELLED("모델 다운로드가 취소되었습니다."),
    MODEL_PULL_TIMEOUT("모델 다운로드 대기 시간이 초과되었습니다.");

    private final String message;
}
