package kr.suhsaechan.ai.exception;

import lombok.Getter;

/**
 * Ollama 서버 통신 중 발생하는 예외
 */
@Getter
public class OllamaException extends RuntimeException {

    private final OllamaErrorCode errorCode;

    public OllamaException(OllamaErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OllamaException(OllamaErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public OllamaException(OllamaErrorCode errorCode, String additionalMessage) {
        super(errorCode.getMessage() + " - " + additionalMessage);
        this.errorCode = errorCode;
    }

    public OllamaException(OllamaErrorCode errorCode, String additionalMessage, Throwable cause) {
        super(errorCode.getMessage() + " - " + additionalMessage, cause);
        this.errorCode = errorCode;
    }
}
