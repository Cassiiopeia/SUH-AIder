package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import kr.suhsaechan.ai.util.FormatUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모델 다운로드 결과 DTO
 * 다운로드 완료 후 성공/실패/취소 상태를 담습니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullResult {

    /**
     * 다운로드한 모델명
     */
    private String modelName;

    /**
     * 다운로드 성공 여부
     */
    private boolean success;

    /**
     * 취소 여부
     */
    private boolean cancelled;

    /**
     * 총 소요 시간 (밀리초)
     */
    private long totalDurationMs;

    /**
     * 실패 시 에러 메시지
     */
    private String errorMessage;

    /**
     * 실패 원인 예외 (있는 경우)
     *
     * <p>{@code PullCallback.onError}가 제거되면서 예외 정보를 담을 자리가 필요해졌습니다.
     * 네트워크 오류 등으로 실패한 경우 원본 예외가 여기 들어옵니다.</p>
     */
    @JsonIgnore
    private Throwable cause;

    /**
     * 성공 결과 생성
     *
     * @param modelName  모델명
     * @param durationMs 소요 시간 (밀리초)
     * @return 성공 PullResult
     */
    public static PullResult success(String modelName, long durationMs) {
        return PullResult.builder()
                .modelName(modelName)
                .success(true)
                .cancelled(false)
                .totalDurationMs(durationMs)
                .build();
    }

    /**
     * 취소 결과 생성
     *
     * @param modelName 모델명
     * @return 취소된 PullResult
     */
    public static PullResult cancelled(String modelName) {
        return PullResult.builder()
                .modelName(modelName)
                .success(false)
                .cancelled(true)
                .errorMessage("다운로드가 취소되었습니다")
                .build();
    }

    /**
     * 실패 결과 생성
     *
     * @param modelName    모델명
     * @param errorMessage 에러 메시지
     * @return 실패 PullResult
     */
    public static PullResult failure(String modelName, String errorMessage) {
        return failure(modelName, errorMessage, null);
    }

    /**
     * 실패 결과 생성 (원인 예외 포함)
     *
     * @param modelName    모델명
     * @param errorMessage 에러 메시지
     * @param cause        원인 예외 (null 가능)
     * @return 실패 PullResult
     */
    public static PullResult failure(String modelName, String errorMessage, Throwable cause) {
        return PullResult.builder()
                .modelName(modelName)
                .success(false)
                .cancelled(false)
                .errorMessage(errorMessage)
                .cause(cause)
                .build();
    }

    /**
     * 소요 시간을 사람이 읽기 쉬운 형식으로 변환
     *
     * @return 예: "2분 30초", "1시간 15분"
     */
    public String getFormattedDuration() {
        return FormatUtils.formatDuration(totalDurationMs);
    }
}
