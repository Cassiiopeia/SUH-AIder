# 임베딩 & 청킹 기능 가이드

> **v0.2.0+**: Ollama /api/embed 기반 텍스트 임베딩 및 자동 청킹 지원

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [빠른 시작](#빠른-시작)
- [임베딩 API](#임베딩-api)
- [텍스트 청킹](#텍스트-청킹)
- [유사도 검색](#유사도-검색)
- [고급 사용법](#고급-사용법)
- [트러블슈팅](#트러블슈팅)

---

## 개요

**임베딩(Embedding)** 은 텍스트를 고차원 벡터로 변환하는 기술입니다. 이를 통해 텍스트 간 의미적 유사도를 계산할 수 있습니다.

### 주요 활용 사례

| 활용 | 설명 |
|------|------|
| **RAG (검색 증강 생성)** | 질문과 유사한 문서 검색 후 LLM에 컨텍스트로 제공 |
| **시맨틱 검색** | 키워드가 아닌 의미 기반 문서 검색 |
| **문서 클러스터링** | 유사한 문서 그룹화 |
| **중복 탐지** | 유사도가 높은 문서 찾기 |

### 지원 모델

```bash
# Ollama에서 임베딩 모델 설치
ollama pull nomic-embed-text     # 768차원, 범용
ollama pull mxbai-embed-large    # 1024차원, 고성능
ollama pull all-minilm           # 384차원, 경량
```

---

## 주요 기능

| 메서드 | 설명 |
|--------|------|
| `embed(model, text)` | 단일 텍스트 임베딩 |
| `embed(model, texts)` | 배치 임베딩 (여러 텍스트) |
| `embed(EmbeddingRequest)` | 상세 옵션으로 임베딩 |
| `embedWithChunking(model, text, config)` | 청킹 + 임베딩 |
| `embedWithChunking(model, text)` | 설정 기반 청킹 + 임베딩 |
| `embedWithChunking(text)` | 기본 모델로 청킹 + 임베딩 |

---

## 빠른 시작

### 1. 설정

```yaml
# application.yml
suh:
  aider:
    embedding:
      default-model: nomic-embed-text  # 기본 임베딩 모델
      truncate: true                   # 컨텍스트 초과 시 자르기
      keep-alive: 5m                   # 모델 메모리 유지

      # 청킹 설정
      chunking:
        enabled: true
        strategy: FIXED_SIZE           # FIXED_SIZE, SENTENCE, PARAGRAPH
        chunk-size: 500
        overlap-size: 50
```

### 2. 단일 텍스트 임베딩

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final SuhAiderEngine engine;

    public void embedText() {
        // 단일 텍스트 임베딩
        List<Double> vector = engine.embed("nomic-embed-text", "안녕하세요, 테스트입니다.");

        System.out.println("벡터 차원: " + vector.size());        // 768
        System.out.println("첫 5개 값: " + vector.subList(0, 5)); // [-0.123, 0.456, ...]
    }
}
```

### 3. 배치 임베딩

```java
public void batchEmbed() {
    List<String> texts = List.of(
        "첫 번째 문장입니다.",
        "두 번째 문장입니다.",
        "세 번째 문장입니다."
    );

    List<List<Double>> vectors = engine.embed("nomic-embed-text", texts);

    System.out.println("생성된 벡터 수: " + vectors.size());  // 3
}
```

---

## 임베딩 API

### 간편 사용

```java
// 단일 텍스트
List<Double> vector = engine.embed("nomic-embed-text", "텍스트");

// 배치
List<List<Double>> vectors = engine.embed("nomic-embed-text", List.of("텍스트1", "텍스트2"));
```

### 상세 옵션 사용

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .model("nomic-embed-text")
    .input("임베딩할 텍스트")
    .truncate(true)              // 컨텍스트 초과 시 자르기
    .keepAlive("10m")            // 모델 메모리 유지 시간
    .dimensions(512)             // 차원 수 지정 (모델 지원 시)
    .build();

EmbeddingResponse response = engine.embed(request);

// 응답 정보
String model = response.getModel();                     // 사용된 모델
List<List<Double>> embeddings = response.getEmbeddings(); // 벡터들
Long duration = response.getTotalDuration();            // 처리 시간 (나노초)
```

### 설정 기반 기본값

```yaml
# application.yml
suh:
  aider:
    embedding:
      default-model: nomic-embed-text
      truncate: true
      keep-alive: 5m
      dimensions: null  # null이면 모델 기본값 사용
```

---

## 텍스트 청킹

긴 텍스트는 임베딩 모델의 컨텍스트 길이를 초과할 수 있습니다. 청킹을 통해 자동으로 분할합니다.

### 청킹 전략

| 전략 | 설명 | 적합한 경우 |
|------|------|-------------|
| `FIXED_SIZE` | 고정 문자 수로 분할 | 일반적인 텍스트 |
| `SENTENCE` | 문장 단위로 분할 | 기사, 소설 등 |
| `PARAGRAPH` | 단락(`\n\n`) 단위로 분할 | 문서, 마크다운 |

### 방법 1: 코드에서 직접 설정

```java
// 고정 크기 청킹 (500자, 50자 오버랩)
ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);

// 문장 단위 청킹 (최대 1000자)
ChunkingConfig config = ChunkingConfig.sentence(1000);

// 단락 단위 청킹 (최대 2000자)
ChunkingConfig config = ChunkingConfig.paragraph(2000);
```

```java
String longText = "긴 문서 내용...".repeat(100);

EmbeddingResponse response = engine.embedWithChunking(
    "nomic-embed-text",
    longText,
    ChunkingConfig.fixedSize(500, 50)
);

System.out.println("청크 수: " + response.getEmbeddings().size());
```

### 방법 2: 설정 파일 사용

```yaml
# application.yml
suh:
  aider:
    embedding:
      default-model: nomic-embed-text
      chunking:
        enabled: true
        strategy: FIXED_SIZE
        chunk-size: 500
        overlap-size: 50
```

```java
// 설정 기반 청킹 사용
EmbeddingResponse response = engine.embedWithChunking("nomic-embed-text", longText);

// 기본 모델까지 설정 사용
EmbeddingResponse response = engine.embedWithChunking(longText);
```

### 오버랩(Overlap)의 중요성

오버랩은 청크 간 의미 손실을 방지합니다.

```
원본: "인공지능은 미래의 핵심 기술입니다. 다양한 분야에 활용됩니다."

오버랩 없음 (chunk=30):
  청크1: "인공지능은 미래의 핵심 기술입니다."
  청크2: "다양한 분야에 활용됩니다."
  → "핵심 기술" 관련 검색 시 청크2 누락 가능

오버랩 있음 (chunk=30, overlap=10):
  청크1: "인공지능은 미래의 핵심 기술입니다."
  청크2: "핵심 기술입니다. 다양한 분야에 활용됩니다."
  → 연속성 유지
```

**권장 오버랩**: 청크 크기의 10~20%

---

## 유사도 검색

임베딩 벡터를 사용하여 텍스트 간 유사도를 계산합니다.

### 코사인 유사도

```java
@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final SuhAiderEngine engine;

    public void findSimilar() {
        String model = "nomic-embed-text";
        String query = "인공지능";
        List<String> documents = List.of(
            "인공지능은 미래의 기술입니다.",
            "오늘 날씨가 좋습니다.",
            "딥러닝과 머신러닝은 AI의 일부입니다.",
            "맛있는 음식을 먹었습니다."
        );

        // 임베딩 생성
        List<Double> queryVector = engine.embed(model, query);
        List<List<Double>> docVectors = engine.embed(model, documents);

        // 유사도 계산 및 정렬
        for (int i = 0; i < documents.size(); i++) {
            double similarity = cosineSimilarity(queryVector, docVectors.get(i));
            System.out.printf("%.4f - %s%n", similarity, documents.get(i));
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
```

**출력 예시**:
```
0.8234 - 딥러닝과 머신러닝은 AI의 일부입니다.
0.7891 - 인공지능은 미래의 기술입니다.
0.2345 - 오늘 날씨가 좋습니다.
0.1234 - 맛있는 음식을 먹었습니다.
```

### 벡터 DB 연동 (예시)

```java
// PostgreSQL + pgvector
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query(value = """
        SELECT * FROM documents
        ORDER BY embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Document> findSimilar(@Param("queryVector") String queryVector,
                                @Param("limit") int limit);
}
```

```java
// 사용 예
List<Double> queryVector = engine.embed(model, "검색어");
String vectorStr = queryVector.toString();  // "[0.1, 0.2, ...]"
List<Document> similar = repository.findSimilar(vectorStr, 5);
```

---

## 고급 사용법

### RAG 파이프라인

```java
@Service
@RequiredArgsConstructor
public class RagService {

    private final SuhAiderEngine engine;
    private final DocumentRepository documentRepository;

    public String answer(String question) {
        String embeddingModel = "nomic-embed-text";
        String chatModel = "gemma3:4b";

        // 1. 질문 임베딩
        List<Double> questionVector = engine.embed(embeddingModel, question);

        // 2. 유사 문서 검색 (Top 3)
        List<Document> relevantDocs = documentRepository.findSimilar(
            questionVector.toString(), 3);

        // 3. 컨텍스트 구성
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n---\n\n"));

        // 4. LLM에 질문
        String prompt = String.format("""
            다음 문서를 참고하여 질문에 답하세요.

            [문서]
            %s

            [질문]
            %s

            [답변]
            """, context, question);

        return engine.generate(chatModel, prompt);
    }
}
```

### 문서 인덱싱

```java
public void indexDocuments(List<String> documents) {
    String model = "nomic-embed-text";

    // 청킹 + 임베딩
    for (String doc : documents) {
        EmbeddingResponse response = engine.embedWithChunking(
            model, doc, ChunkingConfig.fixedSize(500, 50));

        // 각 청크와 벡터를 DB에 저장
        List<List<Double>> vectors = response.getEmbeddings();
        List<String> chunks = TextChunker.chunk(doc, ChunkingConfig.fixedSize(500, 50));

        for (int i = 0; i < chunks.size(); i++) {
            saveToVectorDB(chunks.get(i), vectors.get(i));
        }
    }
}
```

### 대용량 배치 처리

```java
public void batchProcess(List<String> texts) {
    String model = "nomic-embed-text";
    int batchSize = 100;

    for (int i = 0; i < texts.size(); i += batchSize) {
        List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));

        List<List<Double>> vectors = engine.embed(model, batch);

        // 벡터 저장
        for (int j = 0; j < batch.size(); j++) {
            saveToVectorDB(batch.get(j), vectors.get(j));
        }

        System.out.printf("처리 완료: %d / %d%n", i + batch.size(), texts.size());
    }
}
```

---

## 트러블슈팅

### EMBEDDING_CONTEXT_OVERFLOW

**증상**: 입력 텍스트가 모델 컨텍스트를 초과

**해결**:
```java
// 방법 1: truncate 활성화
EmbeddingRequest request = EmbeddingRequest.builder()
    .model("nomic-embed-text")
    .input(longText)
    .truncate(true)  // 초과분 자름
    .build();

// 방법 2: 청킹 사용
EmbeddingResponse response = engine.embedWithChunking(
    "nomic-embed-text", longText, ChunkingConfig.fixedSize(500, 50));
```

### 빈 응답

**증상**: `EMPTY_RESPONSE` 에러

**원인**: 빈 텍스트 입력 또는 모델 문제

**해결**:
```java
// 입력 검증
if (text == null || text.trim().isEmpty()) {
    throw new IllegalArgumentException("텍스트가 비어있습니다");
}

// 모델 확인
List<ModelInfo> models = engine.getAvailableModels();
boolean hasModel = models.stream()
    .anyMatch(m -> m.getName().contains("nomic-embed"));
```

### 모델 로드 시간이 오래 걸림

**증상**: 첫 요청이 느림

**해결**:
```yaml
# 모델 메모리 유지 시간 증가
suh:
  aider:
    embedding:
      keep-alive: 1h  # 1시간
```

```java
// 또는 요청에서 직접 설정
EmbeddingRequest request = EmbeddingRequest.builder()
    .model("nomic-embed-text")
    .input(text)
    .keepAlive("-1")  // 영구 유지
    .build();
```

### 차원 수 불일치

**증상**: 저장된 벡터와 새 벡터의 차원이 다름

**원인**: 다른 모델 사용 또는 dimensions 설정 변경

**해결**:
- 동일한 모델 사용 확인
- dimensions 설정 고정
- 재인덱싱 필요

---

## DTO 참조

### EmbeddingRequest

| 필드 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `model` | String | - | 임베딩 모델명 (필수) |
| `input` | Object | - | 텍스트 또는 텍스트 리스트 (필수) |
| `truncate` | Boolean | true | 컨텍스트 초과 시 자르기 |
| `keepAlive` | String | "5m" | 모델 메모리 유지 시간 |
| `dimensions` | Integer | null | 임베딩 차원 수 |

### EmbeddingResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | String | 사용된 모델명 |
| `embeddings` | List<List<Double>> | 임베딩 벡터들 |
| `totalDuration` | Long | 전체 처리 시간 (나노초) |
| `loadDuration` | Long | 모델 로드 시간 (나노초) |
| `promptEvalCount` | Integer | 평가된 프롬프트 토큰 수 |

### ChunkingConfig

| 필드 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `strategy` | Strategy | FIXED_SIZE | 청킹 전략 |
| `chunkSize` | int | 500 | 청크당 최대 문자 수 |
| `overlapSize` | int | 50 | 청크 간 오버랩 문자 수 |
| `enabled` | boolean | false | 청킹 활성화 여부 |

| 팩토리 메서드 | 설명 |
|---------------|------|
| `fixedSize(size, overlap)` | 고정 크기 청킹 |
| `sentence(maxSize)` | 문장 단위 청킹 |
| `paragraph(maxSize)` | 단락 단위 청킹 |

---

## 관련 문서

- [README.md](../README.md) - 전체 기능 개요
- [Function Calling 가이드](FUNCTION_CALLING_GUIDE.md) - 의도 분류 기능
- [JSON Schema 가이드](JSON_SCHEMA_GUIDE.md) - 구조화된 응답 생성
- [모델 관리 가이드](MODEL_MANAGEMENT_GUIDE.md) - 모델 다운로드/삭제
- [Ollama Embedding 문서](https://github.com/ollama/ollama/blob/main/docs/api.md#embeddings) - 공식 API 문서
