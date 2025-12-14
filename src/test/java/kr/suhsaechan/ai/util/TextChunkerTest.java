package kr.suhsaechan.ai.util;

import kr.suhsaechan.ai.model.ChunkingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextChunker 유틸리티 테스트
 */
@SpringBootTest
@ActiveProfiles("dev")
class TextChunkerTest {

  @Test
  void mainTest() throws Exception {
    testBasicFixedSizeChunking();
    testOverlapApplied();
    testTextSmallerThanChunkSize();
    testSentenceChunking();
    testParagraphChunking();
    testNullInput();
    testEmptyInput();
    testChunkingDisabled();
    testFactoryMethods();
  }

  void testBasicFixedSizeChunking() {
    // 1000자 텍스트를 500자 청크, 50자 오버랩으로 분할
    String text = "A".repeat(1000);
    ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);

    List<String> chunks = TextChunker.chunk(text, config);

    assertFalse(chunks.isEmpty());
    // step = 500 - 50 = 450
    // 청크: 0-500, 450-950, 900-1000
    assertEquals(3, chunks.size());
    assertEquals(500, chunks.get(0).length());
    assertEquals(500, chunks.get(1).length());
    assertEquals(100, chunks.get(2).length());
    System.out.println("✅ 고정 크기 청킹 테스트 통과 - " + chunks.size() + "개 청크 생성");
  }

  void testOverlapApplied() {
    // 오버랩 확인: ABCDEFGHIJ를 5자씩, 2자 오버랩
    String text = "ABCDEFGHIJ";
    ChunkingConfig config = ChunkingConfig.fixedSize(5, 2);

    List<String> chunks = TextChunker.chunk(text, config);

    // step = 5 - 2 = 3
    // 청크: 0-5(ABCDE), 3-8(DEFGH), 6-10(GHIJ)
    assertEquals(3, chunks.size());
    assertEquals("ABCDE", chunks.get(0));
    assertEquals("DEFGH", chunks.get(1));
    assertEquals("GHIJ", chunks.get(2));
    System.out.println("✅ 오버랩 테스트 통과 - " + chunks);
  }

  void testTextSmallerThanChunkSize() {
    // 청크 사이즈보다 작은 텍스트
    String text = "짧은 텍스트";
    ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);

    List<String> chunks = TextChunker.chunk(text, config);

    assertEquals(1, chunks.size());
    assertEquals(text, chunks.get(0));
    System.out.println("✅ 작은 텍스트 테스트 통과");
  }

  void testSentenceChunking() {
    // 문장 단위 청킹
    String text = "첫 번째 문장입니다. 두 번째 문장이에요! 세 번째 문장인가요? 네 번째.";
    ChunkingConfig config = ChunkingConfig.sentence(100);

    List<String> chunks = TextChunker.chunk(text, config);

    assertFalse(chunks.isEmpty());
    System.out.println("✅ 문장 청킹 테스트 통과 - " + chunks.size() + "개 청크");
    chunks.forEach(c -> System.out.println("  - " + c));
  }

  void testParagraphChunking() {
    // 단락 단위 청킹
    String text = "첫 번째 단락입니다.\n\n두 번째 단락입니다.\n\n세 번째 단락입니다.";
    ChunkingConfig config = ChunkingConfig.paragraph(100);

    List<String> chunks = TextChunker.chunk(text, config);

    assertFalse(chunks.isEmpty());
    assertTrue(chunks.stream().anyMatch(c -> c.contains("첫 번째")));
    System.out.println("✅ 단락 청킹 테스트 통과 - " + chunks.size() + "개 청크");
  }

  void testNullInput() {
    ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);
    List<String> chunks = TextChunker.chunk(null, config);
    assertTrue(chunks.isEmpty());
    System.out.println("✅ null 입력 테스트 통과");
  }

  void testEmptyInput() {
    ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);
    List<String> chunks = TextChunker.chunk("", config);
    assertTrue(chunks.isEmpty());
    System.out.println("✅ 빈 문자열 테스트 통과");
  }

  void testChunkingDisabled() {
    // 청킹 비활성화 시 원본 반환
    String text = "긴 텍스트".repeat(100);
    ChunkingConfig config = ChunkingConfig.builder()
        .enabled(false)
        .build();

    List<String> chunks = TextChunker.chunk(text, config);

    assertEquals(1, chunks.size());
    assertEquals(text, chunks.get(0));
    System.out.println("✅ 청킹 비활성화 테스트 통과");
  }

  void testFactoryMethods() {
    // fixedSize 팩토리
    ChunkingConfig fixed = ChunkingConfig.fixedSize(300, 30);
    assertEquals(ChunkingConfig.Strategy.FIXED_SIZE, fixed.getStrategy());
    assertEquals(300, fixed.getChunkSize());
    assertEquals(30, fixed.getOverlapSize());
    assertTrue(fixed.isEnabled());

    // sentence 팩토리
    ChunkingConfig sentence = ChunkingConfig.sentence(1000);
    assertEquals(ChunkingConfig.Strategy.SENTENCE, sentence.getStrategy());
    assertTrue(sentence.isEnabled());

    // paragraph 팩토리
    ChunkingConfig paragraph = ChunkingConfig.paragraph(2000);
    assertEquals(ChunkingConfig.Strategy.PARAGRAPH, paragraph.getStrategy());
    assertTrue(paragraph.isEnabled());

    System.out.println("✅ 팩토리 메서드 테스트 통과");
  }
}
