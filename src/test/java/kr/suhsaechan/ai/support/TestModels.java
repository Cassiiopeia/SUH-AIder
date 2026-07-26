package kr.suhsaechan.ai.support;

/**
 * 통합 테스트에서 사용하는 실제 모델명 상수
 *
 * <p>테스트마다 모델명을 하드코딩하면 서버에서 모델이 사라졌을 때 어디를 고쳐야 하는지
 * 흩어집니다. v1.x는 존재하지 않는 {@code qwen3-vl:2b}, {@code granite4:350m}을 참조해
 * Pull 테스트가 실패하고 있었습니다.</p>
 */
public final class TestModels {

    private TestModels() {
        // 상수 클래스
    }

    /**
     * 일반 생성/대화용 소형 모델
     */
    public static final String CHAT = "gemma4:e2b";

    /**
     * 임베딩 모델
     */
    public static final String EMBEDDING = "embeddinggemma:latest";

    /**
     * Function Calling에 실제로 tool_calls를 생성하는 모델
     *
     * <p>{@code functiongemma:latest}는 268M으로 너무 작아 도구를 고르지 않고
     * 평문으로 답해버리므로 테스트에 쓰지 않습니다.</p>
     */
    public static final String FUNCTION_CALLING = "gemma4:e2b";

    /**
     * Pull 테스트용 초소형 모델 (다운로드가 빨라야 함)
     */
    public static final String PULL_TARGET = "hf.co/Mungert/HyperCLOVAX-SEED-Text-Instruct-0.5B-GGUF:IQ3_M";
}
