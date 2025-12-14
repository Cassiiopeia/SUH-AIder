package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.model.EmbeddingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * 임베딩 API 통합 테스트
 *
 * 실행 방법:
 * 1. src/test/resources/application-dev.yml에 서버 설정
 * 2. IDE에서 mainTest() 실행
 */
@SpringBootTest
@ActiveProfiles("dev")
class EmbeddingIntegrationTest {

  @Autowired
  private SuhAiderEngine engine;

  @Autowired
  private SuhAiderConfig config;

  @Test
  void mainTest() throws Exception {
    testBasicEmbed();
    testBatchEmbed();
    testDetailedEmbed();
    testChunkingEmbed();
    testSimilaritySearch();
  }

  void testBasicEmbed() {
    // 단일 텍스트 임베딩
    String model = config.getEmbedding().getDefaultModel();
    String text = "안녕하세요, 테스트 텍스트입니다.";

    List<Double> vector = engine.embed(model, text);

    System.out.println("✅ 기본 임베딩 테스트");
    System.out.println("  - 벡터 차원: " + vector.size());
    System.out.println("  - 첫 5개 값: " + vector.subList(0, Math.min(5, vector.size())));
  }

  void testBatchEmbed() {
    // 배치 임베딩
    String model = config.getEmbedding().getDefaultModel();
    List<String> texts = List.of(
        "첫 번째 문장입니다.",
        "두 번째 문장입니다.",
        "세 번째 문장입니다."
    );

    List<List<Double>> vectors = engine.embed(model, texts);

    System.out.println("✅ 배치 임베딩 테스트");
    System.out.println("  - 생성된 벡터 수: " + vectors.size());
  }

  void testDetailedEmbed() {
    // 상세 옵션 임베딩
    EmbeddingRequest request = EmbeddingRequest.builder()
        .model(config.getEmbedding().getDefaultModel())
        .input("상세 옵션 테스트 텍스트")
        .truncate(true)
        .keepAlive("10m")
        .build();

    EmbeddingResponse response = engine.embed(request);

    System.out.println("✅ 상세 옵션 임베딩 테스트");
    System.out.println("  - 모델: " + response.getModel());
    System.out.println("  - 처리 시간: " + (response.getTotalDuration() / 1_000_000) + "ms");
  }

  void testChunkingEmbed() {
    // 청킹 + 임베딩
    String model = config.getEmbedding().getDefaultModel();
    String longText = "이것은 청킹 테스트를 위한 긴 텍스트입니다. ".repeat(100);
    ChunkingConfig chunkingConfig = ChunkingConfig.fixedSize(500, 50);

    EmbeddingResponse response = engine.embedWithChunking(model, longText, chunkingConfig);

    System.out.println("✅ 청킹 + 임베딩 테스트");
    System.out.println("  - 원본 길이: " + longText.length() + "자");
    System.out.println("  - 생성된 청크: " + response.getEmbeddings().size() + "개");
  }

  void testSimilaritySearch() {
    // 코사인 유사도 계산 예제
    String model = config.getEmbedding().getDefaultModel();
    String query = "인공지능";
    List<String> documents = List.of(
        "인공지능은 미래의 기술입니다.",
        "오늘 날씨가 좋습니다.",
        "딥러닝과 머신러닝은 AI의 일부입니다.",
        "맛있는 음식을 먹었습니다."
    );

    List<Double> queryVector = engine.embed(model, query);
    List<List<Double>> docVectors = engine.embed(model, documents);

    System.out.println("✅ 유사도 검색 테스트");
    System.out.println("  - 쿼리: " + query);
    for (int i = 0; i < documents.size(); i++) {
      double similarity = cosineSimilarity(queryVector, docVectors.get(i));
      System.out.printf("  - 문서 %d: %.4f - %s%n", i + 1, similarity, documents.get(i));
    }
  }

  private double cosineSimilarity(List<Double> a, List<Double> b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.size(); i++) {
      dotProduct += a.get(i) * b.get(i);
      normA += a.get(i) * a.get(i);
      normB += b.get(i) * b.get(i);
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
