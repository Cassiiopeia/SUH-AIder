package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;

/**
 * 모델 다운로드 콜백 인터페이스
 * 모델 다운로드 진행률을 실시간으로 처리할 때 사용합니다.
 *
 * <p>사용 예제:</p>
 * <pre>
 * PullHandle handle = suhAiderEngine.pullModelStream("llama3.2:70b", new PullCallback() {
 *     &#64;Override
 *     public void onProgress(PullProgress progress) {
 *         System.out.printf("다운로드 중: %s (%.1f%%)\n",
 *             progress.getStatus(), progress.getPercent());
 *     }
 *
 *     &#64;Override
 *     public void onComplete(PullResult result) {
 *         if (result.isSuccess()) {
 *             System.out.println("다운로드 완료! 소요시간: " + result.getFormattedDuration());
 *         } else if (result.isCancelled()) {
 *             System.out.println("다운로드가 취소되었습니다.");
 *         } else {
 *             System.out.println("다운로드 실패: " + result.getErrorMessage());
 *         }
 *     }
 *
 *     &#64;Override
 *     public void onError(Throwable error) {
 *         System.err.println("에러 발생: " + error.getMessage());
 *     }
 * });
 *
 * // 나중에 취소 가능
 * handle.cancel();
 * </pre>
 *
 * @see SuhAiderEngine#pullModelStream(String, PullCallback)
 * @see PullProgress
 * @see PullResult
 * @see PullHandle
 */
public interface PullCallback {

    /**
     * 다운로드 진행 상태가 업데이트될 때마다 호출됩니다.
     * 레이어별 다운로드 진행률, 매니페스트 풀링, 검증 등의 상태를 전달합니다.
     *
     * @param progress 현재 진행 상태 (status, completed, total 등)
     */
    void onProgress(PullProgress progress);

    /**
     * 다운로드가 완료되었을 때 호출됩니다.
     * 성공, 실패, 취소 모든 경우에 호출됩니다.
     *
     * @param result 다운로드 결과 (성공 여부, 소요 시간, 에러 메시지 등)
     */
    void onComplete(PullResult result);

    /**
     * 다운로드 처리 중 예외가 발생했을 때 호출됩니다.
     * 네트워크 오류, 파싱 오류 등 예상치 못한 예외를 전달합니다.
     *
     * @param error 발생한 예외 (주로 SuhAiderException)
     */
    void onError(Throwable error);
}
