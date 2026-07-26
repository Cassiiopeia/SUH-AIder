package kr.suhsaechan.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.FunctionRequest;
import kr.suhsaechan.ai.model.FunctionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Function Calling API
 *
 * <p>Tool 정의를 받아 AI가 어떤 함수를 호출할지 고르게 합니다.
 * 내부적으로는 Chat API에 {@code tools}를 실어 보냅니다.</p>
 *
 * <p><b>모델 선택 주의:</b> 모든 모델이 tool_calls를 생성하지는 않습니다.
 * 작은 모델은 도구를 고르는 대신 평문으로 답해버리는 경우가 있으니,
 * 실제로 {@code tool_calls}를 반환하는지 확인하고 사용하세요.</p>
 */
@Slf4j
public class FunctionApi {

    private final ChatApi chatApi;
    private final ObjectMapper objectMapper;

    public FunctionApi(ChatApi chatApi, ObjectMapper objectMapper) {
        this.chatApi = chatApi;
        this.objectMapper = objectMapper;
    }

    /**
     * Function Calling 수행
     *
     * @param request 요청 (model, userText, systemPrompt, tools 필수)
     * @return 선택된 Tool과 인자
     * @throws SuhAiderException 파라미터 오류, 통신 오류 시
     */
    public FunctionResponse functionCall(FunctionRequest request) {
        validate(request);

        log.debug("FunctionCall 호출 - 모델: {}, Tool 개수: {}",
                request.getModel(), request.getTools().size());

        ChatResponse chatResponse = chatApi.chat(request.toChatRequest());
        FunctionResponse response = FunctionResponse.fromChatResponse(chatResponse, objectMapper);

        log.info("FunctionCall 완료 - toolName: {}, hasToolCall: {}",
                response.getToolName(), response.isHasToolCall());

        return response;
    }

    private void validate(FunctionRequest request) {
        if (request == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "요청이 null입니다");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (!StringUtils.hasText(request.getUserText())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "userText가 비어있습니다");
        }
        if (!StringUtils.hasText(request.getSystemPrompt())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "systemPrompt가 비어있습니다");
        }
        if (request.getTools() == null || request.getTools().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER,
                    "tools가 비어있습니다. 최소 1개 이상의 FunctionTool을 정의해야 합니다");
        }
    }
}
