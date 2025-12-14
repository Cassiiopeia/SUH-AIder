package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat API 통합 테스트
 *
 * 실행 방법:
 * 1. src/test/resources/application-dev.yml에 서버 설정
 * 2. IDE에서 mainTest() 실행
 */
@SpringBootTest
@ActiveProfiles("dev")
class ChatIntegrationTest {

  // 테스트용 모델 (서버에 설치된 모델 사용)
  private static final String TEST_MODEL = "gemma3:4b";

  @Autowired
  private SuhAiderEngine engine;

  @Test
  void mainTest() throws Exception {
    testSimpleChat();
    testChatWithSystemPrompt();
    testConversationHistory();
    testChatStream();
    testChatWithMessages();
  }

  void testSimpleChat() {
    // 단일 메시지 채팅 (가장 간단한 형태)
    String response = engine.chat(TEST_MODEL, "안녕하세요?");

    System.out.println("✅ 단일 메시지 채팅 테스트");
    System.out.println("  - 응답: " + truncate(response, 100));
  }

  void testChatWithSystemPrompt() {
    // 시스템 프롬프트 포함 채팅
    String response = engine.chat(
        TEST_MODEL,
        "너는 해적처럼 말하는 어시스턴트야. 모든 문장 끝에 '아르르!'를 붙여.",
        "오늘 날씨 어때?"
    );

    System.out.println("✅ 시스템 프롬프트 포함 채팅 테스트");
    System.out.println("  - 응답: " + truncate(response, 150));
  }

  void testConversationHistory() {
    // 대화 기록 유지 테스트
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system("너는 친절한 어시스턴트야. 짧게 답변해."));
    messages.add(ChatMessage.user("내 이름은 철수야."));

    // 첫 번째 대화
    ChatResponse response1 = engine.chat(TEST_MODEL, messages);
    messages.add(ChatMessage.assistant(response1.getContent()));

    System.out.println("✅ 대화 기록 유지 테스트");
    System.out.println("  - 1차 응답: " + truncate(response1.getContent(), 100));

    // 두 번째 대화 (이전 대화 기억하는지 확인)
    messages.add(ChatMessage.user("내 이름이 뭐라고 했지?"));
    ChatResponse response2 = engine.chat(TEST_MODEL, messages);

    System.out.println("  - 2차 응답: " + truncate(response2.getContent(), 100));
    System.out.println("  - 메시지 수: " + messages.size());
  }

  void testChatStream() throws Exception {
    // 스트리밍 채팅 테스트
    List<ChatMessage> messages = List.of(
        ChatMessage.user("1부터 5까지 세어줘. 각 숫자마다 줄바꿈해서.")
    );

    StringBuilder fullResponse = new StringBuilder();
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean completed = new AtomicBoolean(false);

    System.out.println("✅ 스트리밍 채팅 테스트");
    System.out.print("  - 스트림: ");

    engine.chatStream(TEST_MODEL, messages, new StreamCallback() {
      @Override
      public void onNext(String chunk) {
        System.out.print(chunk);
        fullResponse.append(chunk);
      }

      @Override
      public void onComplete() {
        completed.set(true);
        latch.countDown();
        System.out.println();
        System.out.println("  - 완료! 총 " + fullResponse.length() + "자");
      }

      @Override
      public void onError(Throwable error) {
        System.err.println("  - 에러: " + error.getMessage());
        latch.countDown();
      }
    });

    // 최대 60초 대기
    latch.await(60, TimeUnit.SECONDS);
  }

  void testChatWithMessages() {
    // ChatRequest 빌더 사용 테스트
    ChatRequest request = ChatRequest.builder()
        .model(TEST_MODEL)
        .messages(List.of(
            ChatMessage.system("너는 JSON으로만 응답하는 봇이야."),
            ChatMessage.user("사과의 색깔을 JSON으로 알려줘.")
        ))
        .stream(false)
        .build();

    ChatResponse response = engine.chat(request);

    System.out.println("✅ ChatRequest 빌더 테스트");
    System.out.println("  - 모델: " + response.getModel());
    System.out.println("  - 응답: " + truncate(response.getContent(), 150));
    System.out.println("  - 처리 시간: " + response.getTotalDurationMs() + "ms");
    System.out.println("  - 완료 여부: " + response.getDone());
  }

  /**
   * 문자열 자르기 (긴 응답 출력용)
   */
  private String truncate(String text, int maxLength) {
    if (text == null) return "null";
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength) + "...";
  }
}
