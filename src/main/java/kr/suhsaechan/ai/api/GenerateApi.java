package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.SuhAiderRequest;
import kr.suhsaechan.ai.model.SuhAiderResponse;
import kr.suhsaechan.ai.service.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 텍스트 생성 API (Ollama {@code /api/generate})
 *
 * <p>v2.0부터 {@code responseSchema}는 Ollama 네이티브 {@code format} 파라미터로 전달됩니다.
 * v1.x는 프롬프트에 영어 지시문을 덧붙이는 방식이었는데, 이는 Ollama가 구조화 출력을
 * 지원하기 전의 우회책이었습니다. 스트리밍에서도 동일하게 스키마가 적용됩니다.</p>
 */
@Slf4j
public class GenerateApi {

    private static final String PATH = "/api/generate";

    private final SuhAiderHttpExecutor http;
    private final SuhAiderCustomizer customizer;

    public GenerateApi(SuhAiderHttpExecutor http, SuhAiderCustomizer customizer) {
        this.http = http;
        this.customizer = customizer;
    }

    /**
     * 텍스트 생성
     *
     * @param request 요청 (model, prompt 필수)
     * @return 생성 결과
     * @throws SuhAiderException 파라미터 오류, 통신 오류 시
     */
    public SuhAiderResponse generate(SuhAiderRequest request) {
        validate(request);

        JsonSchema schema = SchemaSupport.resolve(request.getResponseSchema(), customizer);
        SuhAiderRequest payload = toPayload(request, schema, false);

        log.debug("Generate 호출 - 모델: {}, 프롬프트 길이: {}, 스키마: {}",
                request.getModel(), request.getPrompt().length(), schema != null ? "있음" : "없음");

        SuhAiderResponse response = http.post(PATH, payload, SuhAiderResponse.class);

        if (schema != null) {
            response.setResponse(SchemaSupport.cleanJsonResponse(response.getResponse(), http.objectMapper()));
        }

        log.info("Generate 완료 - 응답 길이: {}, 처리 시간: {}ms",
                response.getResponse() != null ? response.getResponse().length() : 0,
                response.getTotalDuration() != null ? response.getTotalDuration() / 1_000_000 : 0);

        return response;
    }

    /**
     * 간편 텍스트 생성
     *
     * @param model  모델명
     * @param prompt 프롬프트
     * @return 생성된 텍스트
     */
    public String generate(String model, String prompt) {
        SuhAiderResponse response = generate(SuhAiderRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .build());
        return response.getResponse();
    }

    /**
     * 스트리밍 텍스트 생성
     *
     * <p>스키마를 지정하면 조각들을 모두 이어 붙였을 때 유효한 JSON이 됩니다.
     * 조각 단위로는 JSON이 아니므로 중간 정제를 하지 않습니다.</p>
     *
     * @param request  요청 (model, prompt 필수)
     * @param callback 스트리밍 콜백
     */
    public void generateStream(SuhAiderRequest request, StreamCallback callback) {
        try {
            validate(request);
        } catch (SuhAiderException e) {
            callback.onError(e);
            return;
        }

        JsonSchema schema = SchemaSupport.resolve(request.getResponseSchema(), customizer);
        SuhAiderRequest payload = toPayload(request, schema, true);

        log.debug("Generate Stream 호출 - 모델: {}, 스키마: {}",
                request.getModel(), schema != null ? "있음" : "없음");

        http.streamNdjson(PATH, payload, node -> node.path("response").asText(""), callback);
    }

    /**
     * 간편 스트리밍 텍스트 생성
     *
     * @param model    모델명
     * @param prompt   프롬프트
     * @param callback 스트리밍 콜백
     */
    public void generateStream(String model, String prompt, StreamCallback callback) {
        generateStream(SuhAiderRequest.builder()
                .model(model)
                .prompt(prompt)
                .build(), callback);
    }

    private void validate(SuhAiderRequest request) {
        if (request == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "요청이 null입니다");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "프롬프트가 비어있습니다");
        }
    }

    /**
     * 전송용 요청 생성 — 스키마를 format으로 변환하고 내부 필드를 제거
     */
    private SuhAiderRequest toPayload(SuhAiderRequest request, JsonSchema schema, boolean stream) {
        Object format = schema != null ? schema.toFormatObject() : request.getFormat();

        return request.toBuilder()
                .stream(stream)
                .format(format)
                .responseSchema(null)  // Ollama로 전송하지 않는 내부 필드
                .build();
    }
}
