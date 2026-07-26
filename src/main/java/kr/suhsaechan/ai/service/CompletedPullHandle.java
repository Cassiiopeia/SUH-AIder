package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;

/**
 * 이미 종료된 상태로 반환되는 PullHandle
 *
 * <p>파라미터 검증 실패처럼 통신을 시작조차 하지 못한 경우에 사용합니다.
 * v1.x의 {@code createDummyHandle()}은 {@code isDone()=true}이면서
 * {@code isCancelled()=false}, {@code getLatestProgress()=null}을 반환해
 * "성공적으로 끝난 다운로드"와 구별되지 않았습니다.</p>
 */
public class CompletedPullHandle implements PullHandle {

    private final String modelName;

    /**
     * @param modelName 대상 모델명
     */
    public CompletedPullHandle(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public void cancel() {
        // 이미 종료된 작업이라 취소할 것이 없다
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public PullProgress getLatestProgress() {
        return null;
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
