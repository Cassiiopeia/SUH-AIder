package kr.suhsaechan.ai.http;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import okhttp3.Call;
import okhttp3.Response;

import java.io.IOException;

/**
 * 아직 실행하지 않은 스트리밍 호출
 *
 * <p>호출자는 {@link #open()} 전에 이 객체를 받아 취소 핸들을 만들 수 있습니다.
 * 모델 다운로드처럼 "핸들을 즉시 돌려주고 실제 통신은 백그라운드에서" 해야 하는
 * 경우에 필요합니다. OkHttp 타입이 {@code http} 패키지 밖으로 새지 않도록
 * 취소 동작을 이 클래스가 감쌉니다.</p>
 */
public class PreparedStream {

    private final Call call;
    private final SuhAiderErrorCode failureCode;
    private volatile boolean cancelled = false;

    PreparedStream(Call call, SuhAiderErrorCode failureCode) {
        this.call = call;
        this.failureCode = failureCode;
    }

    /**
     * 호출을 실행하고 스트림 세션을 연다
     *
     * @return 열린 스트림 세션 (호출자가 close 책임)
     * @throws kr.suhsaechan.ai.exception.SuhAiderException 응답이 실패 상태이거나 통신 오류인 경우
     */
    public StreamSession open() {
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw HttpErrorMapper.fromIoException(e);
        }

        if (!response.isSuccessful()) {
            String body = "";
            try {
                body = response.body() != null ? response.body().string() : "";
            } catch (IOException ignored) {
                // 에러 본문 확보 실패는 상태코드 매핑에 영향을 주지 않는다
            }
            int code = response.code();
            response.close();
            throw HttpErrorMapper.fromStatus(code, body, failureCode);
        }

        return new StreamSession(call, response);
    }

    /**
     * 진행 중이거나 대기 중인 호출을 취소
     */
    public void cancel() {
        cancelled = true;
        call.cancel();
    }

    /**
     * 취소 요청 여부
     *
     * @return 취소가 요청됐으면 true
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
