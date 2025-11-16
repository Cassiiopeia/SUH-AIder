package kr.suhsaechan.ai.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ollama 서버 통신 중 발생 가능한 에러 코드
 */
@Getter
@RequiredArgsConstructor
public enum OllamaErrorCode {

    // 설정 관련 에러
    API_KEY_MISSING("API 키가 설정되지 않았습니다. suh.ai.api-key를 설정해주세요."),
    BASE_URL_INVALID("서버 URL이 올바르지 않습니다."),

    // 네트워크 에러
    NETWORK_ERROR("네트워크 연결 중 오류가 발생했습니다."),
    CONNECTION_TIMEOUT("서버 연결 시간이 초과되었습니다."),
    READ_TIMEOUT("서버 응답 대기 시간이 초과되었습니다."),

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
    FORBIDDEN("접근 권한이 없습니다.");

    private final String message;
}
