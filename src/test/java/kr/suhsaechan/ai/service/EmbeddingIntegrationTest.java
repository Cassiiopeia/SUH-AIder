package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.model.EmbeddingResponse;
import kr.suhsaechan.ai.support.TestModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 임베딩 API 통합 테스트 (실서버 필요)
 *
 * <pre>
 * SUH_AIDER_IT=true ./gradlew integrationTest
 * </pre>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SUH_AIDER_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("dev")
class EmbeddingIntegrationTest {

  @Autowired
  private SuhAiderEngine engine;

  @Test
  @DisplayName("단일 텍스트 임베딩")
  void basicEmbed() {
    List<Double> vector = engine.embed(TestModels.EMBEDDING, "안녕하세요");

    assertFalse(vector.isEmpty());
  }

  @Test
  @DisplayName("배치 임베딩은 입력 개수만큼 벡터를 돌려준다")
  void batchEmbed() {
    List<List<Double>> vectors = engine.embed(TestModels.EMBEDDING,
        List.of("안녕하세요", "반갑습니다", "좋은 하루"));

    assertEquals(3, vectors.size());
    assertFalse(vectors.get(0).isEmpty());
  }

  @Test
  @DisplayName("상세 옵션 임베딩")
  void detailedEmbed() {
    EmbeddingResponse response = engine.embed(EmbeddingRequest.builder()
        .model(TestModels.EMBEDDING)
        .input("상세 옵션 테스트")
        .truncate(true)
        .keepAlive("1m")
        .build());

    assertEquals(1, response.getEmbeddings().size());
  }

  @Test
  @DisplayName("긴 텍스트는 청킹 후 각 청크가 임베딩된다")
  void chunkingEmbed() {
    String longText = "인공지능은 인간의 학습 능력을 컴퓨터로 구현한 기술이다. ".repeat(20);

    EmbeddingResponse response = engine.embedWithChunking(
        TestModels.EMBEDDING, longText, ChunkingConfig.fixedSize(200, 20));

    assertTrue(response.getEmbeddings().size() > 1, "긴 텍스트는 여러 청크로 나뉘어야 한다");
  }

  @Test
  @DisplayName("유사한 문장이 더 높은 코사인 유사도를 갖는다")
  void similaritySearch() {
    List<Double> base = engine.embed(TestModels.EMBEDDING, "고양이는 귀여운 동물이다");
    List<Double> similar = engine.embed(TestModels.EMBEDDING, "강아지는 사랑스러운 동물이다");
    List<Double> different = engine.embed(TestModels.EMBEDDING, "자바는 객체지향 프로그래밍 언어다");

    double similarScore = cosineSimilarity(base, similar);
    double differentScore = cosineSimilarity(base, different);

    assertTrue(similarScore > differentScore,
        "동물 문장끼리가 더 유사해야 한다 (유사=" + similarScore + ", 무관=" + differentScore + ")");
  }

  private double cosineSimilarity(List<Double> a, List<Double> b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;

    for (int i = 0; i < a.size(); i++) {
      dot += a.get(i) * b.get(i);
      normA += a.get(i) * a.get(i);
      normB += b.get(i) * b.get(i);
    }

    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
