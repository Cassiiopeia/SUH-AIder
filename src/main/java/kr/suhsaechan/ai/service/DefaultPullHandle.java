package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.http.PreparedStream;
import kr.suhsaechan.ai.model.PullProgress;

/**
 * PullHandle 기본 구현체
 *
 * <p>취소는 {@link PreparedStream}에 위임합니다. v1.x는 OkHttp {@code Call}을 직접 들고
 * 있었는데, 그러면 서비스 계층이 HTTP 구현에 묶입니다.</p>
 */
public class DefaultPullHandle implements PullHandle {

    private final String modelName;
    private final PreparedStream stream;
    private volatile boolean done = false;
    private volatile PullProgress latestProgress;

    /**
     * @param modelName 다운로드할 모델명
     * @param stream    취소 가능한 스트림
     */
    public DefaultPullHandle(String modelName, PreparedStream stream) {
        this.modelName = modelName;
        this.stream = stream;
    }

    @Override
    public void cancel() {
        if (!done) {
            stream.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return stream.isCancelled();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public PullProgress getLatestProgress() {
        return latestProgress;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    /**
     * 진행 상태 업데이트 (내부 사용)
     *
     * @param progress 새로운 진행 상태
     */
    public void updateProgress(PullProgress progress) {
        this.latestProgress = progress;
    }

    /**
     * 완료 상태로 변경 (내부 사용)
     */
    public void markDone() {
        this.done = true;
    }
}
