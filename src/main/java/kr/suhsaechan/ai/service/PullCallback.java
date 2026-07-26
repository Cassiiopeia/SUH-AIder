package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;

/**
 * 모델 다운로드 콜백 인터페이스
 * 모델 다운로드 진행률을 실시간으로 처리할 때 사용합니다.
 *
 * <p><b>종료 계약:</b> 성공·실패·취소 어떤 경로로 끝나든 {@link #onComplete(PullResult)}가
 * <b>정확히 한 번</b> 호출됩니다. 결과 종류는 {@link PullResult}로 구분하세요.</p>
 *
 * <p>v1.x에는 {@code onError(Throwable)}가 따로 있었고, HTTP 오류는 {@code onError}로,
 * 응답 본문의 {@code error} 필드는 {@code onComplete(failure)}로 갈렸습니다. 같은 "실패"가
 * 두 경로로 나뉘는 바람에 {@code onComplete}에서만 결과를 받는 코드가 빈 결과를 만나
 * NPE를 냈습니다. v2.0에서는 종료 지점을 하나로 합쳐 이 유형의 버그를 구조적으로 막습니다.
 * 예외 객체가 필요하면 {@link PullResult#getCause()}를 사용하세요.</p>
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
     * <p>이 메서드에서 발생한 예외는 로그만 남기고 무시합니다.
     * 소비자 측 표시 로직의 오류가 다운로드를 중단시키지 않도록 하기 위함입니다.</p>
     *
     * @param progress 현재 진행 상태 (status, completed, total 등)
     */
    void onProgress(PullProgress progress);

    /**
     * 다운로드가 종료되었을 때 호출됩니다 (정확히 1회).
     * 성공, 실패, 취소 모든 경우가 여기로 들어옵니다.
     *
     * @param result 다운로드 결과 (성공 여부, 소요 시간, 에러 메시지, 원인 예외)
     */
    void onComplete(PullResult result);
}
