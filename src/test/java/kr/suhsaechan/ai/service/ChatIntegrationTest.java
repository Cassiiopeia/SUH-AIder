package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.support.TestModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat API 통합 테스트 (실서버 필요)
 *
 * <p>실행 방법:</p>
 * <pre>
 * # 1. src/test/resources/application-dev.yml 에 서버 설정 (gitignore 대상)
 * # 2. 실행
 * SUH_AIDER_IT=true ./gradlew integrationTest
 * </pre>
 *
 * <p>{@code ./gradlew test}는 {@code integration} 태그를 제외하므로 실행되지 않습니다.
 * 실서버 의존 테스트가 CI 결과를 흔들지 않도록 분리했습니다.</p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SUH_AIDER_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("dev")
class ChatIntegrationTest {

  @Autowired
  private SuhAiderEngine engine;

  @Test
  @DisplayName("단일 메시지 대화")
  void simpleChat() {
    String response = engine.chat(TestModels.CHAT, "안녕하세요?");

    assertNotNull(response);
    assertFalse(response.isBlank());
  }

  @Test
  @DisplayName("시스템 프롬프트 포함 대화")
  void chatWithSystemPrompt() {
    String response = engine.chat(
        TestModels.CHAT,
        "너는 해적처럼 말하는 어시스턴트야. 모든 문장 끝에 '아르르!'를 붙여.",
        "오늘 날씨 어때?");

    assertNotNull(response);
    assertFalse(response.isBlank());
  }

  @Test
  @DisplayName("대화 기록을 유지한다")
  void conversationHistory() {
    ChatResponse response = engine.chat(ChatRequest.builder()
        .model(TestModels.CHAT)
        .messages(List.of(
            ChatMessage.system("너는 친절한 어시스턴트야. 짧게 답변해."),
            ChatMessage.user("내 이름은 철수야."),
            ChatMessage.assistant("반갑습니다, 철수님!"),
            ChatMessage.user("내 이름이 뭐라고 했지?")))
        .build());

    assertNotNull(response.getContent());
    assertTrue(response.getContent().contains("철수"), "대화 기록이 유지되면 이름을 기억해야 한다");
  }

  @Test
  @DisplayName("responseSchema가 네이티브 format으로 동작한다")
  void chatWithSchema() {
    ChatResponse response = engine.chat(ChatRequest.builder()
        .model(TestModels.CHAT)
        .messages(List.of(ChatMessage.user("홍길동은 30살이다. 이름과 나이를 뽑아줘.")))
        .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
        .build());

    String content = response.getContent();
    assertNotNull(content);
    assertTrue(content.trim().startsWith("{"), "구조화 출력이면 JSON이어야 한다: " + content);
  }

  @Test
  @DisplayName("스트리밍 응답을 조각 단위로 받는다")
  void chatStream() throws Exception {
    List<String> chunks = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    engine.chatStream(TestModels.CHAT, List.of(ChatMessage.user("1부터 5까지 세어줘")),
        new StreamCallback() {
          @Override
          public void onNext(String chunk) {
            chunks.add(chunk);
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }

          @Override
          public void onError(Throwable error) {
            latch.countDown();
            throw new AssertionError(error);
          }
        });

    assertTrue(latch.await(2, TimeUnit.MINUTES), "스트리밍이 시간 내에 끝나야 한다");
    assertFalse(chunks.isEmpty());
  }
}
