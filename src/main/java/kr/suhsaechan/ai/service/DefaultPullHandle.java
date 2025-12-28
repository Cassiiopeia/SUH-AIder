package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;
import okhttp3.Call;

/**
 * PullHandle 기본 구현체
 * OkHttp Call을 사용하여 다운로드 취소 및 상태 관리를 수행합니다.
 */
public class DefaultPullHandle implements PullHandle {

    private final String modelName;
    private final Call httpCall;
    private volatile boolean cancelled = false;
    private volatile boolean done = false;
    private volatile PullProgress latestProgress;

    /**
     * DefaultPullHandle 생성자
     *
     * @param modelName 다운로드할 모델명
     * @param httpCall  OkHttp Call 객체 (취소용)
     */
    public DefaultPullHandle(String modelName, Call httpCall) {
        this.modelName = modelName;
        this.httpCall = httpCall;
    }

    @Override
    public void cancel() {
        if (!done) {
            cancelled = true;
            httpCall.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
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
