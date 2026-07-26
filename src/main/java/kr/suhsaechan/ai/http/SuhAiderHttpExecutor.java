package kr.suhsaechan.ai.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.service.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Ollama 서버와의 HTTP 통신 단일 지점
 *
 * <p>요청 조립, 인증 헤더 부착, 실행, 응답 역직렬화, 예외 변환을 전담합니다.
 * v1.x에서는 이 흐름이 {@code SuhAiderEngine} 안에 다섯 번 복제돼 있었습니다.</p>
 *
 * <p>이 클래스는 Ollama의 도메인 개념(모델, 프롬프트 등)을 알지 못합니다.
 * 경로와 페이로드, 기대하는 응답 타입만 받습니다.</p>
 */
@Slf4j
public class SuhAiderHttpExecutor {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SuhAiderConfig config;

    public SuhAiderHttpExecutor(OkHttpClient httpClient, ObjectMapper objectMapper, SuhAiderConfig config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * 내부에서 사용하는 JSON 매퍼 반환
     *
     * <p>API 계층이 별도 매퍼를 만들지 않고 이 인스턴스를 재사용하도록 노출합니다.</p>
     *
     * @return ObjectMapper
     */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    // ========== 동기 호출 ==========

    /**
     * GET 요청 후 원문 문자열 반환 (Health Check 등 비-JSON 응답용)
     *
     * @param path 경로 (예: "/")
     * @return 응답 본문 문자열
     */
    public String getRaw(String path) {
        return executeForString(newRequest(path).get().build(), null);
    }

    /**
     * GET 요청 후 지정 타입으로 역직렬화
     *
     * @param path 경로
     * @param type 응답 타입
     * @param <T>  응답 타입 파라미터
     * @return 역직렬화된 응답
     */
    public <T> T get(String path, Class<T> type) {
        return executeForObject(newRequest(path).get().build(), type, null);
    }

    /**
     * POST(JSON) 요청 후 지정 타입으로 역직렬화
     *
     * @param path    경로
     * @param payload 요청 본문 객체
     * @param type    응답 타입
     * @param <T>     응답 타입 파라미터
     * @return 역직렬화된 응답
     */
    public <T> T post(String path, Object payload, Class<T> type) {
        Request request = newRequest(path).post(jsonBody(payload)).build();
        return executeForObject(request, type, null);
    }

    /**
     * DELETE(JSON 본문) 요청
     *
     * @param path        경로
     * @param payload     요청 본문 객체
     * @param failureCode 인증/미존재 외 실패에 사용할 에러 코드
     */
    public void delete(String path, Object payload, SuhAiderErrorCode failureCode) {
        Request request = newRequest(path).delete(jsonBody(payload)).build();
        executeForString(request, failureCode);
    }

    // ========== 스트리밍 ==========

    /**
     * 스트리밍 호출을 준비 (아직 실행하지 않음)
     *
     * @param path              경로
     * @param payload           요청 본문 객체
     * @param readTimeoutSecond 읽기 타임아웃 초 (0이면 무제한, null이면 기본값)
     * @param failureCode       실패 시 사용할 에러 코드
     * @return 실행/취소 가능한 준비된 스트림
     */
    public PreparedStream prepareStream(String path, Object payload,
                                        Integer readTimeoutSecond, SuhAiderErrorCode failureCode) {
        Request request = newRequest(path).post(jsonBody(payload)).build();

        OkHttpClient client = httpClient;
        if (readTimeoutSecond != null) {
            // 대용량 다운로드는 기본 읽기 타임아웃(120초)으로는 끊긴다.
            // newBuilder()는 커넥션 풀과 디스패처를 공유하므로 비용이 크지 않다.
            client = httpClient.newBuilder()
                    .readTimeout(readTimeoutSecond, TimeUnit.SECONDS)
                    .build();
        }

        Call call = client.newCall(request);
        return new PreparedStream(call, failureCode);
    }

    /**
     * NDJSON 스트리밍 호출을 실행하고 {@link StreamCallback}으로 전달
     *
     * <p>종료 통지({@code onComplete} 또는 {@code onError})는 어떤 경로로 끝나든
     * 정확히 한 번만 발생합니다. v1.x에서는 예상 못 한 런타임 예외가 나면 종료 통지가
     * 아예 누락돼 호출자가 영원히 대기하는 문제가 있었습니다.</p>
     *
     * @param path           경로
     * @param payload        요청 본문 객체
     * @param chunkExtractor JSON 노드에서 텍스트 조각을 추출하는 함수
     * @param callback       스트리밍 콜백
     */
    public void streamNdjson(String path, Object payload,
                             Function<JsonNode, String> chunkExtractor, StreamCallback callback) {
        AtomicBoolean terminated = new AtomicBoolean(false);

        try (StreamSession session = prepareStream(path, payload, null, null).open()) {
            NdjsonStreamReader.read(session.source(), objectMapper, node -> {
                String chunk = chunkExtractor.apply(node);
                if (StringUtils.hasLength(chunk)) {
                    // 소비자 콜백의 예외가 스트림 전체를 끝내지 않도록 격리한다
                    try {
                        callback.onNext(chunk);
                    } catch (Exception e) {
                        log.warn("onNext 콜백 처리 중 예외 발생 (무시됨): {}", e.getMessage());
                    }
                }
                return node.path("done").asBoolean(false);
            });

            if (terminated.compareAndSet(false, true)) {
                callback.onComplete();
            }

        } catch (SuhAiderException e) {
            if (terminated.compareAndSet(false, true)) {
                callback.onError(e);
            }
        } catch (IOException e) {
            if (terminated.compareAndSet(false, true)) {
                callback.onError(HttpErrorMapper.fromIoException(e));
            }
        } catch (Throwable t) {
            if (terminated.compareAndSet(false, true)) {
                callback.onError(t);
            }
        }
    }

    // ========== 내부 구현 ==========

    /**
     * 인증 헤더가 부착된 요청 빌더 생성
     */
    private Request.Builder newRequest(String path) {
        Request.Builder builder = new Request.Builder().url(config.getBaseUrl() + path);

        SuhAiderConfig.Security security = config.getSecurity();
        if (security != null && StringUtils.hasText(security.getApiKey())) {
            String headerValue = security.getHeaderValueFormat().replace("{value}", security.getApiKey());
            builder.addHeader(security.getHeaderName(), headerValue);
            // 값은 로그에 남기지 않는다. v1.x는 앞 4자를 노출해 키 일부가 새어 나갔다.
            log.debug("인증 헤더 부착 - {}", security.getHeaderName());
        }

        return builder;
    }

    /**
     * 객체를 JSON 요청 본문으로 직렬화
     */
    private RequestBody jsonBody(Object payload) {
        try {
            return RequestBody.create(objectMapper.writeValueAsString(payload), JSON);
        } catch (JsonProcessingException e) {
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        }
    }

    /**
     * 요청 실행 후 본문 문자열 반환
     */
    private String executeForString(Request request, SuhAiderErrorCode failureCode) {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("요청 실패 - {} {} → HTTP {}: {}",
                        request.method(), request.url().encodedPath(), response.code(), body);
                throw HttpErrorMapper.fromStatus(response.code(), body, failureCode);
            }

            return body;

        } catch (IOException e) {
            log.error("통신 오류 - {} {}: {}", request.method(), request.url().encodedPath(), e.getMessage());
            throw HttpErrorMapper.fromIoException(e);
        }
    }

    /**
     * 요청 실행 후 지정 타입으로 역직렬화
     */
    private <T> T executeForObject(Request request, Class<T> type, SuhAiderErrorCode failureCode) {
        String body = executeForString(request, failureCode);

        if (!StringUtils.hasText(body)) {
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE);
        }

        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            log.error("응답 파싱 실패 - {}: {}", request.url().encodedPath(), e.getMessage());
            throw new SuhAiderException(SuhAiderErrorCode.JSON_PARSE_ERROR, e);
        }
    }
}
