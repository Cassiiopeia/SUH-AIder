package kr.suhsaechan.ai.model;

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
        return PullResult.builder()
                .modelName(modelName)
                .success(false)
                .cancelled(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 소요 시간을 사람이 읽기 쉬운 형식으로 변환
     *
     * @return 예: "2분 30초", "1시간 15분"
     */
    public String getFormattedDuration() {
        if (totalDurationMs <= 0) {
            return "N/A";
        }

        long seconds = totalDurationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d시간 %d분", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds % 60);
        } else {
            return String.format("%d초", seconds);
        }
    }
}
