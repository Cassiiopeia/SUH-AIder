package kr.suhsaechan.ai.model;

import kr.suhsaechan.ai.util.FormatUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 모델 다운로드 진행률 DTO
 * Ollama /api/pull 스트리밍 응답을 담습니다.
 *
 * <p>응답 예제:</p>
 * <pre>
 * {"status":"pulling manifest"}
 * {"status":"downloading f00c...","digest":"sha256:f00c...","total":987654321,"completed":12345678}
 * {"status":"verifying sha256 digest"}
 * {"status":"success"}
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullProgress {

    /**
     * 현재 상태
     * 예: "pulling manifest", "downloading", "verifying sha256 digest", "success"
     */
    private String status;

    /**
     * 다운로드 중인 레이어 다이제스트 (해시값)
     * 예: "sha256:f00c1234..."
     */
    private String digest;

    /**
     * 완료된 바이트 수
     */
    private long completed;

    /**
     * 전체 바이트 수
     */
    private long total;

    /**
     * 다운로드 진행률 계산 (0.0 ~ 100.0)
     *
     * @return 진행률 (퍼센트)
     */
    public double getPercent() {
        if (total <= 0) {
            return 0.0;
        }
        return (completed * 100.0) / total;
    }

    /**
     * 사람이 읽기 쉬운 진행 상태 문자열
     *
     * @return 예: "1.2 GB / 4.0 GB (30.0%)"
     */
    public String getFormattedProgress() {
        if (total <= 0) {
            return status != null ? status : "준비 중...";
        }
        return String.format("%s / %s (%.1f%%)",
                FormatUtils.formatBytes(completed),
                FormatUtils.formatBytes(total),
                getPercent());
    }

    /**
     * 다운로드 중인지 확인
     *
     * @return status가 downloading을 포함하면 true
     */
    public boolean isDownloading() {
        return status != null && status.toLowerCase().contains("download");
    }

    /**
     * 성공 상태인지 확인
     *
     * @return status가 "success"면 true
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

}
