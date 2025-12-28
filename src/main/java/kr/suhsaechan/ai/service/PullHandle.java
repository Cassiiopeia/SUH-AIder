package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;

/**
 * 모델 다운로드 핸들 인터페이스
 * 진행 중인 다운로드의 상태 확인 및 취소에 사용합니다.
 *
 * <p>사용 예제:</p>
 * <pre>
 * // 다운로드 시작
 * PullHandle handle = suhAiderEngine.pullModelStream("llama3.2:70b", callback);
 *
 * // 상태 확인
 * System.out.println("모델: " + handle.getModelName());
 * System.out.println("진행률: " + handle.getLatestProgress().getPercent() + "%");
 *
 * // 취소
 * if (userRequestedCancel) {
 *     handle.cancel();
 * }
 *
 * // 완료 여부 확인
 * if (handle.isDone()) {
 *     System.out.println("다운로드 완료");
 * }
 * </pre>
 *
 * @see SuhAiderEngine#pullModelStream(String, PullCallback)
 * @see PullCallback
 */
public interface PullHandle {

    /**
     * 다운로드를 취소합니다.
     * HTTP 연결이 즉시 종료되며, PullCallback.onComplete()가 취소 결과와 함께 호출됩니다.
     */
    void cancel();

    /**
     * 취소되었는지 확인합니다.
     *
     * @return 취소되었으면 true
     */
    boolean isCancelled();

    /**
     * 다운로드가 완료되었는지 확인합니다.
     * 성공, 실패, 취소 모든 경우에 true를 반환합니다.
     *
     * @return 완료되었으면 true
     */
    boolean isDone();

    /**
     * 최신 진행 상태를 가져옵니다.
     *
     * @return 가장 최근에 수신된 PullProgress (없으면 null)
     */
    PullProgress getLatestProgress();

    /**
     * 다운로드 중인 모델명을 가져옵니다.
     *
     * @return 모델명
     */
    String getModelName();
}
