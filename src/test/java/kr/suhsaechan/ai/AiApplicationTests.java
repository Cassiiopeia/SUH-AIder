package kr.suhsaechan.ai;

import kr.suhsaechan.ai.api.ChatApi;
import kr.suhsaechan.ai.api.EmbeddingApi;
import kr.suhsaechan.ai.api.FunctionApi;
import kr.suhsaechan.ai.api.GenerateApi;
import kr.suhsaechan.ai.api.ModelApi;
import kr.suhsaechan.ai.api.PullApi;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 자동 설정으로 Bean이 모두 등록되는지 확인
 *
 * <p>{@code load-on-startup=false}로 두어 컨텍스트 초기화 중 실서버를 호출하지 않습니다.
 * v1.x는 이 설정이 없어 테스트가 뜰 때마다 실제 AI 서버로 나갔습니다.</p>
 */
@SpringBootTest(properties = {
        "suh.aider.base-url=http://localhost:11434",
        "suh.aider.model-refresh.load-on-startup=false",
        "suh.aider.model-refresh.scheduling-enabled=false"
})
class AiApplicationTests {

    @Autowired
    private SuhAiderEngine engine;

    @Autowired
    private ModelApi modelApi;

    @Autowired
    private GenerateApi generateApi;

    @Autowired
    private ChatApi chatApi;

    @Autowired
    private EmbeddingApi embeddingApi;

    @Autowired
    private PullApi pullApi;

    @Autowired
    private FunctionApi functionApi;

    @Test
    @DisplayName("도메인 API와 파사드가 모두 Bean으로 등록된다")
    void contextLoads() {
        assertNotNull(modelApi);
        assertNotNull(generateApi);
        assertNotNull(chatApi);
        assertNotNull(embeddingApi);
        assertNotNull(pullApi);
        assertNotNull(functionApi);
        assertNotNull(engine);
    }

    @Test
    @DisplayName("파사드는 주입된 API와 동일한 인스턴스에 위임한다")
    void facadeDelegatesToSameBeans() {
        // 소비자가 ChatApi만 주입받아 대체해도 파사드와 같은 객체를 다룬다
        assertSame(chatApi, engine.getChatApi());
        assertSame(modelApi, engine.getModelApi());
        assertSame(pullApi, engine.getPullApi());
    }
}
