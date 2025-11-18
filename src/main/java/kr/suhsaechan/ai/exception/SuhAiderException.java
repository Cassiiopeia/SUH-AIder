package kr.suhsaechan.ai.exception;

import lombok.Getter;

/**
 * SUH-AIDER 서버 통신 중 발생하는 예외
 */
@Getter
public class SuhAiderException extends RuntimeException {

    private final SuhAiderErrorCode errorCode;

    public SuhAiderException(SuhAiderErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SuhAiderException(SuhAiderErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public SuhAiderException(SuhAiderErrorCode errorCode, String additionalMessage) {
        super(errorCode.getMessage() + " - " + additionalMessage);
        this.errorCode = errorCode;
    }

    public SuhAiderException(SuhAiderErrorCode errorCode, String additionalMessage, Throwable cause) {
        super(errorCode.getMessage() + " - " + additionalMessage, cause);
        this.errorCode = errorCode;
    }
}
