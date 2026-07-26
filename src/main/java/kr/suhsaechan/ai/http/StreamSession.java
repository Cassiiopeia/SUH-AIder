package kr.suhsaechan.ai.http;

import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.Closeable;

/**
 * 열려 있는 스트리밍 응답 세션
 *
 * <p>취소를 위해 {@link Call}을, 읽기를 위해 {@link BufferedSource}를 함께 들고 있습니다.
 * try-with-resources로 닫으면 응답이 정리됩니다.</p>
 */
public class StreamSession implements Closeable {

    private final Call call;
    private final Response response;
    private final BufferedSource source;

    /**
     * @param call     취소 가능한 HTTP Call
     * @param response 성공 상태의 응답
     * @throws SuhAiderException 응답 본문이 없는 경우
     */
    public StreamSession(Call call, Response response) {
        this.call = call;
        this.response = response;

        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
        }
        this.source = body.source();
    }

    /**
     * 스트림 소스 반환
     *
     * @return 응답 본문 소스
     */
    public BufferedSource source() {
        return source;
    }

    /**
     * 진행 중인 호출 반환 (취소용)
     *
     * @return HTTP Call
     */
    public Call call() {
        return call;
    }

    @Override
    public void close() {
        response.close();
    }
}
