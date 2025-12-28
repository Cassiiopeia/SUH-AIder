# SUH-AIDER

AI 서버와 간편하게 통신할 수 있는 Spring Boot 라이브러리입니다.

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
<!-- 이 섹션은 .github/workflows/PROJECT-README-VERSION-UPDATE.yaml에 의해 자동으로 업데이트됩니다 -->
<!-- 수정하지마세요 자동으로 동기화 됩니다 -->
## 최신 버전 : v1.1.2 (2025-12-28)

[전체 버전 기록 보기](CHANGELOG.md)
<!-- END-AUTO-VERSION-SECTION -->

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [설치](#설치)
- [빠른 시작](#빠른-시작)
- [설정](#설정)
- [사용 예제](#사용-예제)
- [API 레퍼런스](#api-레퍼런스)
- [테스트](#테스트)
- [라이선스](#라이선스)

### 📚 상세 가이드

| 가이드 | 설명 |
|--------|------|
| [Function Calling 가이드](docs/FUNCTION_CALLING_GUIDE.md) | FunctionGemma 기반 의도 분류 |
| [JSON Schema 가이드](docs/JSON_SCHEMA_GUIDE.md) | 구조화된 JSON 응답 생성 |
| [모델 관리 가이드](docs/MODEL_MANAGEMENT_GUIDE.md) | 모델 다운로드(Pull) 및 삭제 |
| [임베딩 & 청킹 가이드](docs/EMBEDDING_GUIDE.md) | 텍스트 임베딩 및 자동 분할 |
| [스트리밍 가이드](docs/STREAMING_GUIDE.md) | 실시간 토큰 단위 응답 |

---

## 개요

**SUH-AIDER**는 AI 서버(`https://ai.suhsaechan.kr`)와의 통신을 간소화하는 Spring Boot 라이브러리입니다.

### 특징
- ✅ **Auto-Configuration**: Spring Boot 자동 설정 지원
- ✅ **간편한 API**: 직관적인 메서드로 AI 서버 통신
- ✅ **스트리밍 응답**: GPT처럼 실시간 토큰 단위 응답
- ✅ **JSON 응답 강제**: JSON Schema 기반 구조화된 응답 보장
- ✅ **임베딩 API**: 텍스트 임베딩 벡터 생성 (Ollama `/api/embed`)
- ✅ **텍스트 청킹**: 긴 텍스트 자동 분할 및 임베딩
- ✅ **대화형 Chat API**: 세션 기반 대화 기록 유지
- ✅ **OkHttp 기반**: 안정적이고 효율적인 HTTP 통신
- ✅ **타입 안전**: 완벽한 Java 타입 지원
- ✅ **예외 처리**: 명확한 에러 코드 및 메시지
- ✅ **Function Calling**: FunctionGemma 등 Tool 기반 의도 분류

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **Health Check** | AI 서버 상태 확인 |
| **모델 목록 조회** | 설치된 AI 모델 목록 가져오기 |
| **텍스트 생성 (Generate)** | AI 프롬프트로 텍스트 생성 |
| **스트리밍 응답** | GPT처럼 실시간 토큰 단위 응답 표시 |
| **JSON 응답 강제** | JSON Schema로 구조화된 응답 보장 |
| **임베딩 생성 (Embed)** | 텍스트를 벡터로 변환 (RAG, 유사도 검색용) |
| **텍스트 청킹** | 긴 텍스트를 자동으로 분할하여 임베딩 |
| **대화형 Chat API** | 세션 기반 대화 기록 유지 (/api/chat) |
| **Function Calling** | Tool 기반 의도 분류 (FunctionGemma 등) |
| **모델 다운로드 (Pull)** | Ollama 모델 다운로드 (진행률, 취소, 병렬 지원) |
| **모델 삭제 (Delete)** | Ollama 모델 삭제 |
| **간편 API** | 한 줄로 AI 응답 받기 |

---

## 설치

### Gradle

```gradle
dependencies {
    implementation 'kr.suhsaechan:suh-aider:0.0.10'
}
```

### Maven

```xml
<dependency>
    <groupId>kr.suhsaechan</groupId>
    <artifactId>suh-aider</artifactId>
    <version>0.0.10</version>
</dependency>
```

---

## 빠른 시작

### 1. 설정 파일 작성

`src/main/resources/application.yml`:

```yaml
suh:
  aider:
    base-url: https://ai.suhsaechan.kr
    security:
      api-key: ${AI_API_KEY}  # 환경변수 사용 권장
```

### 2. 서비스 주입 및 사용

```java
import kr.suhsaechan.ai.service.SuhAiderEngine;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyService {

    private final SuhAiderEngine suhAiderEngine;

    public void example() {
        // 간편 사용
        String response = suhAiderEngine.generate("gemma3:4b", "Hello, AI!");
        System.out.println(response);
    }
}
```

### 3. 실행

```bash
# 환경변수 설정
export AI_API_KEY=your-api-key

# 애플리케이션 실행
./gradlew bootRun
```

---

## 설정

### 전체 설정 옵션

```yaml
suh:
  aider:
    #==========================================================================
    # 기본 설정
    #==========================================================================

    # AI 서버 기본 URL
    # 기본값: https://ai.suhsaechan.kr
    # 로컬 Ollama 서버: http://localhost:11434
    base-url: https://ai.suhsaechan.kr

    # Auto-Configuration 활성화 여부
    # 기본값: true
    # false로 설정하면 SUH-AIDER Bean이 생성되지 않습니다
    enabled: true

    #==========================================================================
    # HTTP 타임아웃 설정
    #==========================================================================

    # HTTP 연결 타임아웃 (초)
    # 기본값: 30
    # 서버와 연결을 맺는 데 걸리는 최대 시간
    connect-timeout: 30

    # HTTP 읽기 타임아웃 (초)
    # 기본값: 120
    # AI 응답 생성 시간을 고려하여 긴 시간 설정 권장
    # 큰 모델이나 긴 응답이 필요한 경우 더 늘려야 할 수 있음
    read-timeout: 120

    # HTTP 쓰기 타임아웃 (초)
    # 기본값: 30
    # 요청 데이터를 서버로 전송하는 최대 시간
    write-timeout: 30

    #==========================================================================
    # Security Header 설정 (선택적)
    # 인증이 필요한 서버에서만 설정하세요
    # 설정하지 않으면 인증 헤더를 추가하지 않습니다
    #==========================================================================
    security:
      # HTTP 헤더 이름
      # 기본값: X-API-Key
      # 다른 예시: Authorization, X-Custom-Auth
      header-name: X-API-Key

      # 헤더 값 포맷
      # 기본값: {value} (API 키 값 그대로)
      # {value}는 api-key 값으로 치환됩니다
      # Bearer 토큰: "Bearer {value}"
      # 커스텀: "CustomScheme {value}"
      header-value-format: "{value}"

      # API 인증 키 (선택적)
      # 설정하지 않으면 인증 헤더를 추가하지 않습니다
      # 환경변수 사용 권장: ${AI_API_KEY}
      api-key: ${AI_API_KEY:}

    #==========================================================================
    # 모델 목록 자동 갱신 설정
    # 서버에서 사용 가능한 모델 목록을 캐싱하고 자동으로 갱신합니다
    #==========================================================================
    model-refresh:
      # 초기화 시 모델 목록 로드 여부
      # 기본값: true
      # true: Bean 초기화 시 서버에서 모델 목록을 자동으로 로드
      # false: 수동 호출(refreshModels()) 전까지 모델 목록 로드 안 함
      load-on-startup: true

      # 스케줄링 활성화 여부
      # 기본값: false (기본적으로 비활성화)
      # true: cron 표현식에 따라 자동으로 모델 목록 갱신
      # false: 초기화 시에만 로드하고 자동 갱신하지 않음
      scheduling-enabled: false

      # 갱신 스케줄 Cron 표현식
      # 기본값: "0 0 4 * * *" (매일 오전 4시)
      # 형식: 초 분 시 일 월 요일
      # 예시:
      #   - "0 0 4 * * *": 매일 오전 4시
      #   - "0 0 */6 * * *": 6시간마다
      #   - "0 0 0 * * MON": 매주 월요일 자정
      #   - "0 30 9 * * MON-FRI": 평일 오전 9시 30분
      cron: "0 0 4 * * *"

      # Cron 표현식 시간대
      # 기본값: Asia/Seoul
      # 예시: UTC, America/New_York, Europe/London, Asia/Tokyo
      timezone: Asia/Seoul

    #==========================================================================
    # 임베딩 설정
    # 텍스트를 벡터로 변환하는 임베딩 기능 설정
    #==========================================================================
    embedding:
      # 기본 임베딩 모델
      # 기본값: nomic-embed-text
      default-model: nomic-embed-text

      # 컨텍스트 초과 시 입력 자르기
      # 기본값: true
      # false로 설정하면 컨텍스트 초과 시 에러 반환
      truncate: true

      # 모델 메모리 유지 시간
      # 기본값: 5m
      # 예시: "5m", "1h", "-1" (영구)
      keep-alive: 5m

      # 임베딩 차원 수 (null = 모델 기본값)
      # 일부 모델에서만 지원
      # dimensions: 768

      # 청킹 설정 (긴 텍스트 분할)
      chunking:
        # 청킹 활성화 여부
        # 기본값: false
        enabled: false

        # 청킹 전략
        # 기본값: FIXED_SIZE
        # 옵션: FIXED_SIZE (고정 문자 수), SENTENCE (문장 단위), PARAGRAPH (단락 단위)
        strategy: FIXED_SIZE

        # 청크당 최대 문자 수
        # 기본값: 500
        # 토큰 ≈ 문자/4 근사치
        chunk-size: 500

        # 청크 간 오버랩 문자 수
        # 기본값: 50
        # 의미 손실 방지 (10~20% 권장)
        overlap-size: 50
```

### Security Header 설정 예제

#### 1. 기본 X-API-Key 방식 (기본값)
```yaml
suh:
  aider:
    security:
      api-key: ${AI_API_KEY}
```

#### 2. Bearer 토큰 방식
```yaml
suh:
  aider:
    security:
      header-name: Authorization
      header-value-format: "Bearer {value}"
      api-key: ${JWT_TOKEN}
```

#### 3. 커스텀 헤더 방식
```yaml
suh:
  aider:
    security:
      header-name: X-Custom-Auth
      header-value-format: "CustomScheme {value}"
      api-key: ${CUSTOM_TOKEN}
```

#### 4. 인증 없음 (로컬 Ollama 서버)
```yaml
suh:
  aider:
    base-url: http://localhost:11434
    # security 설정 생략 = 인증 헤더 추가 안 함
```

### 환경변수 설정 방법

#### Windows (PowerShell)
```powershell
$env:AI_API_KEY="your-api-key"
```

#### Windows (CMD)
```cmd
set AI_API_KEY=your-api-key
```

#### Linux/Mac
```bash
export AI_API_KEY=your-api-key
```

---

## 사용 예제

### 1. Health Check

```java
boolean isHealthy = suhAiderEngine.isHealthy();
if (isHealthy) {
    System.out.println("서버 정상 작동 중");
}
```

### 2. 모델 목록 조회

```java
// 서버에서 직접 조회 (매번 HTTP 요청)
ModelListResponse response = suhAiderEngine.getModels();
response.getModels().forEach(model -> {
    System.out.println("모델: " + model.getName());
    System.out.println("크기: " + model.getSize() / 1024 / 1024 + " MB");
});

// 캐싱된 목록 조회 (HTTP 요청 없음, 빠름)
List<ModelInfo> cachedModels = suhAiderEngine.getAvailableModels();
cachedModels.forEach(model -> {
    System.out.println("모델: " + model.getName());
});

// 특정 모델 사용 가능 여부 확인
boolean available = suhAiderEngine.isModelAvailable("gemma3:4b");

// 특정 모델 상세 정보 가져오기
Optional<ModelInfo> modelInfo = suhAiderEngine.getModelInfo("gemma3:4b");
modelInfo.ifPresent(info -> {
    System.out.println("모델명: " + info.getName());
    System.out.println("파라미터: " + info.getDetails().getParameterSize());
});

// 모델 목록 수동 갱신
boolean success = suhAiderEngine.refreshModels();
```

### 3. AI 텍스트 생성 (간편)

```java
String response = suhAiderEngine.generate(
    "gemma3:4b",  // 모델명
    "Explain quantum computing in one sentence."  // 프롬프트
);
System.out.println(response);
```

### 4. AI 텍스트 생성 (상세)

```java
SuhAiderRequest request = SuhAiderRequest.builder()
    .model("gemma3:4b")
    .prompt("Write a haiku about coding.")
    .stream(false)
    .build();

SuhAiderResponse response = suhAiderEngine.generate(request);

System.out.println("응답: " + response.getResponse());
System.out.println("처리 시간: " + response.getTotalDuration() / 1_000_000 + " ms");
```

### 5. JSON 응답 강제

**간단한 사용법**:
```java
SuhAiderRequest request = SuhAiderRequest.builder()
    .model("gemma3:4b")
    .prompt("Extract name and age from: John Doe, 30 years old")
    .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
    .build();

SuhAiderResponse response = suhAiderEngine.generate(request);
String json = response.getResponse();  // { "name": "John Doe", "age": 30 }
```

**전역 설정** (@Bean 방식):
```java
@Configuration
public class AiConfig {
    @Bean
    public SuhAiderCustomizer suhAiderCustomizer() {
        return SuhAiderCustomizer.builder()
            .defaultResponseSchema(JsonSchema.of(
                "result", "string",
                "success", "boolean"
            ))
            .build();
    }
}
```

**📚 상세 가이드**: [JSON Schema 사용 가이드](docs/JSON_SCHEMA_GUIDE.md)

### 6. 스트리밍 응답

ChatGPT, Claude처럼 AI가 토큰을 생성할 때마다 실시간으로 응답을 받을 수 있습니다.

**기본 사용법**:
```java
suhAiderEngine.generateStream("gemma3:4b", "안녕하세요!", new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        System.out.print(chunk);  // 토큰 단위로 실시간 출력
    }

    @Override
    public void onComplete() {
        System.out.println("\n완료!");
    }

    @Override
    public void onError(Throwable error) {
        System.err.println("에러: " + error.getMessage());
    }
});
```

**Spring MVC + SSE (Server-Sent Events)**:
```java
@GetMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamGenerate(@RequestParam String prompt) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

    suhAiderEngine.generateStreamAsync("gemma3:4b", prompt, new StreamCallback() {
        @Override
        public void onNext(String chunk) {
            try {
                emitter.send(SseEmitter.event().data(chunk));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }

        @Override
        public void onComplete() {
            emitter.complete();
        }

        @Override
        public void onError(Throwable error) {
            emitter.completeWithError(error);
        }
    });

    return emitter;
}
```

> **주의**: 스트리밍 모드에서는 `responseSchema`가 지원되지 않습니다. JSON 형식 응답이 필요하면 `generate()` 메서드를 사용하세요.

### 7. 대화형 Chat API

Generate API와 달리, 이전 대화 기록을 포함하여 컨텍스트를 유지할 수 있습니다.

**단일 메시지 (가장 간단한 형태)**:
```java
String response = suhAiderEngine.chat("gemma3:4b", "안녕하세요?");
System.out.println(response);
```

**시스템 프롬프트 포함**:
```java
String response = suhAiderEngine.chat(
    "gemma3:4b",
    "너는 해적처럼 말하는 어시스턴트야. 모든 문장 끝에 '아르르!'를 붙여.",
    "오늘 날씨 어때?"
);
```

**대화 기록 유지 (세션 기반 대화)**:
```java
// 대화 기록을 저장할 리스트
List<ChatMessage> messages = new ArrayList<>();
messages.add(ChatMessage.system("너는 친절한 어시스턴트야. 짧게 답변해."));
messages.add(ChatMessage.user("내 이름은 철수야."));

// 첫 번째 대화
ChatResponse response1 = suhAiderEngine.chat("gemma3:4b", messages);
messages.add(ChatMessage.assistant(response1.getContent()));
System.out.println("AI: " + response1.getContent());

// 두 번째 대화 (이전 대화 기억)
messages.add(ChatMessage.user("내 이름이 뭐라고 했지?"));
ChatResponse response2 = suhAiderEngine.chat("gemma3:4b", messages);
System.out.println("AI: " + response2.getContent());  // "철수"라고 기억하고 답변
```

**ChatRequest 빌더 사용**:
```java
ChatRequest request = ChatRequest.builder()
    .model("gemma3:4b")
    .messages(List.of(
        ChatMessage.system("너는 JSON으로만 응답하는 봇이야."),
        ChatMessage.user("사과의 색깔을 JSON으로 알려줘.")
    ))
    .stream(false)
    .build();

ChatResponse response = suhAiderEngine.chat(request);
System.out.println("응답: " + response.getContent());
System.out.println("처리 시간: " + response.getTotalDurationMs() + "ms");
```

**스트리밍 Chat**:
```java
List<ChatMessage> messages = List.of(
    ChatMessage.user("1부터 10까지 세어줘.")
);

suhAiderEngine.chatStream("gemma3:4b", messages, new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        System.out.print(chunk);  // 실시간 출력
    }

    @Override
    public void onComplete() {
        System.out.println("\n완료!");
    }

    @Override
    public void onError(Throwable error) {
        System.err.println("에러: " + error.getMessage());
    }
});
```

**Spring MVC + SSE (비동기 스트리밍)**:
```java
@GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestParam String message, HttpSession session) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

    // 세션에서 대화 기록 가져오기 (없으면 새로 생성)
    @SuppressWarnings("unchecked")
    List<ChatMessage> messages = (List<ChatMessage>) session.getAttribute("chatHistory");
    if (messages == null) {
        messages = new ArrayList<>();
        messages.add(ChatMessage.system("너는 친절한 어시스턴트야."));
        session.setAttribute("chatHistory", messages);
    }

    // 사용자 메시지 추가
    messages.add(ChatMessage.user(message));

    StringBuilder aiResponse = new StringBuilder();

    suhAiderEngine.chatStreamAsync("gemma3:4b", new ArrayList<>(messages), new StreamCallback() {
        @Override
        public void onNext(String chunk) {
            try {
                aiResponse.append(chunk);
                emitter.send(SseEmitter.event().data(chunk));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }

        @Override
        public void onComplete() {
            // AI 응답을 대화 기록에 추가
            messages.add(ChatMessage.assistant(aiResponse.toString()));
            emitter.complete();
        }

        @Override
        public void onError(Throwable error) {
            emitter.completeWithError(error);
        }
    });

    return emitter;
}
```

> **Generate vs Chat 차이점**:
> - `generate()`: 단일 프롬프트, 컨텍스트 없음, `/api/generate` 사용
> - `chat()`: 메시지 배열, 대화 기록 유지 가능, `/api/chat` 사용

### 8. Function Calling (의도 분류)

FunctionGemma 등 Function Calling 지원 모델로 사용자 의도를 분류합니다. RAG 검색 전 라우팅에 유용합니다.

**기본 사용법**:
```java
// 1. Tool 정의
FunctionTool ragTool = FunctionTool.builder()
    .name("route_rag")
    .description("Use when user asks about configuration or location")
    .parameters(List.of(
        FunctionTool.FunctionParameter.required("query", "string", "Search query")
    ))
    .build();

FunctionTool systemTool = FunctionTool.of("route_system", "Use for status or logs");
FunctionTool smalltalkTool = FunctionTool.of("route_smalltalk", "Use for greetings or chitchat");

// 2. 요청 생성 및 호출
FunctionRequest request = FunctionRequest.builder()
    .model("functiongemma")
    .systemPrompt("You are a router. Choose exactly ONE tool call.")
    .tool(ragTool)
    .tool(systemTool)
    .tool(smalltalkTool)
    .userText("SSE 설정 어디서 했지?")
    .build();

FunctionResponse response = suhAiderEngine.functionCall(request);

// 3. 결과 처리
if (response.isHasToolCall()) {
    String toolName = response.getToolName();
    String query = response.getArgumentAsString("query");

    switch (toolName) {
        case "route_rag":
            // RAG 검색 수행
            break;
        case "route_system":
            // 시스템 정보 조회
            break;
        case "route_smalltalk":
            // 간단한 응답 생성
            break;
    }
}
```

**템플릿 패턴 (재사용)**:
```java
// 빌더 템플릿 정의 (한 번)
FunctionRequest.FunctionRequestBuilder myRouter = FunctionRequest.builder()
    .model("functiongemma")
    .systemPrompt("You are a strict router. Choose exactly ONE tool call.")
    .tool(FunctionTool.of("route_rag", "Use for RAG search", "query", "string", "Search query"))
    .tool(FunctionTool.of("route_system", "Use for system status"));

// 여러 요청에 재사용 (userText만 변경)
FunctionResponse response1 = suhAiderEngine.functionCall(
    myRouter.userText("SSE 설정 어디?").build()
);

FunctionResponse response2 = suhAiderEngine.functionCall(
    myRouter.userText("서버 상태 알려줘").build()
);
```

**Enum 파라미터**:
```java
FunctionTool actionTool = FunctionTool.builder()
    .name("route_system")
    .description("Use for system operations")
    .parameters(List.of(
        FunctionTool.FunctionParameter.enumType("action", "Action type",
            "get_status", "get_logs", "check_port")
    ))
    .build();
```

**📚 상세 가이드**: [Function Calling 가이드](docs/FUNCTION_CALLING_GUIDE.md)

### 9. 임베딩 생성 (Embed)

텍스트를 벡터로 변환하여 RAG, 유사도 검색 등에 활용할 수 있습니다.

**단일 텍스트 임베딩**:
```java
// 간편 사용
List<Double> vector = suhAiderEngine.embed("nomic-embed-text", "Hello, World!");
System.out.println("벡터 차원: " + vector.size());
```

**배치 임베딩** (여러 텍스트 한 번에):
```java
List<String> texts = List.of(
    "첫 번째 문장입니다.",
    "두 번째 문장입니다.",
    "세 번째 문장입니다."
);

List<List<Double>> vectors = suhAiderEngine.embed("nomic-embed-text", texts);
System.out.println("생성된 벡터 개수: " + vectors.size());
```

**상세 옵션**:
```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .model("nomic-embed-text")
    .input("임베딩할 텍스트")
    .truncate(true)      // 컨텍스트 초과 시 자르기
    .keepAlive("10m")    // 모델 메모리 유지 시간
    .build();

EmbeddingResponse response = suhAiderEngine.embed(request);
List<List<Double>> embeddings = response.getEmbeddings();
```

### 10. 텍스트 청킹 + 임베딩

긴 텍스트를 자동으로 분할하여 각각 임베딩합니다.

**직접 청킹 설정**:
```java
// 고정 크기 청킹 (500자, 50자 오버랩)
ChunkingConfig config = ChunkingConfig.fixedSize(500, 50);

EmbeddingResponse response = suhAiderEngine.embedWithChunking(
    "nomic-embed-text",
    longText,
    config
);

System.out.println("청크 개수: " + response.getEmbeddings().size());
```

**문장/단락 단위 청킹**:
```java
// 문장 단위 청킹
ChunkingConfig sentenceConfig = ChunkingConfig.sentence(1000);

// 단락 단위 청킹
ChunkingConfig paragraphConfig = ChunkingConfig.paragraph(2000);
```

**application.yml 설정 기반**:
```java
// application.yml의 suh.aider.embedding.chunking 설정 사용
EmbeddingResponse response = suhAiderEngine.embedWithChunking("nomic-embed-text", longText);

// 기본 모델과 청킹 설정 모두 사용
EmbeddingResponse response = suhAiderEngine.embedWithChunking(longText);
```

### 11. 모델 다운로드 (Pull)

Ollama 서버에서 모델을 다운로드합니다. 진행률 확인, 취소, 병렬 다운로드를 지원합니다.

**간단한 동기 다운로드**:
```java
// 다운로드 완료까지 블로킹
boolean success = suhAiderEngine.pullModel("llama3.2");
```

**진행률 콜백 + 취소 지원**:
```java
PullHandle handle = suhAiderEngine.pullModelStream("llama3.2:70b", new PullCallback() {
    @Override
    public void onProgress(PullProgress progress) {
        System.out.printf("다운로드 중: %s (%.1f%%)%n",
            progress.getStatus(), progress.getPercent());
    }

    @Override
    public void onComplete(PullResult result) {
        if (result.isSuccess()) {
            System.out.println("완료! 소요시간: " + result.getFormattedDuration());
        } else if (result.isCancelled()) {
            System.out.println("취소됨");
        } else {
            System.out.println("실패: " + result.getErrorMessage());
        }
    }

    @Override
    public void onError(Throwable error) {
        System.err.println("에러: " + error.getMessage());
    }
});

// 취소하려면
handle.cancel();
```

**비동기 다운로드**:
```java
CompletableFuture<PullResult> future = suhAiderEngine.pullModelAsync("llama3.2");
// 다른 작업 수행...
PullResult result = future.get();
```

**병렬 다운로드**:
```java
CompletableFuture<List<PullResult>> future = suhAiderEngine.pullModelsAsync(
    List.of("llama3.2", "mistral", "gemma3:4b")
);
List<PullResult> results = future.get();
```

### 12. 모델 삭제 (Delete)

```java
// 모델 삭제
boolean success = suhAiderEngine.deleteModel("llama3.2");
if (success) {
    System.out.println("모델 삭제 완료");
}
```

### 13. 예외 처리

```java
try {
    String response = suhAiderEngine.generate("invalid-model", "Hello");
} catch (SuhAiderException e) {
    switch (e.getErrorCode()) {
        case MODEL_NOT_FOUND:
            System.err.println("모델을 찾을 수 없습니다: " + e.getMessage());
            break;
        case UNAUTHORIZED:
            System.err.println("API 키가 올바르지 않습니다");
            break;
        case NETWORK_ERROR:
            System.err.println("네트워크 오류: " + e.getMessage());
            break;
        default:
            System.err.println("오류 발생: " + e.getMessage());
    }
}
```

---

## API 레퍼런스

### SuhAiderEngine

#### `boolean isHealthy()`
AI 서버의 상태를 확인합니다.

**반환값**: 서버가 정상이면 `true`, 아니면 `false`

#### `ModelListResponse getModels()`
설치된 모델 목록을 서버에서 직접 조회합니다. (매번 HTTP 요청 발생)

**반환값**: `ModelListResponse` (모델 목록 포함)
**예외**: `SuhAiderException`

#### `List<ModelInfo> getAvailableModels()`
캐싱된 모델 목록을 반환합니다. HTTP 요청 없이 빠르게 조회할 수 있습니다.

**반환값**: 불변 `List<ModelInfo>` (빈 리스트 가능)

#### `boolean isModelAvailable(String modelName)`
특정 모델이 사용 가능한지 확인합니다.

**파라미터**:
- `modelName`: 확인할 모델명 (예: `"gemma3:4b"`)

**반환값**: 사용 가능하면 `true`. 모델 목록이 초기화되지 않았으면 항상 `true` (서버에서 최종 검증)

#### `Optional<ModelInfo> getModelInfo(String modelName)`
캐싱된 목록에서 특정 모델의 상세 정보를 가져옵니다.

**파라미터**:
- `modelName`: 조회할 모델명 (예: `"gemma3:4b"`)

**반환값**: `Optional<ModelInfo>` (없으면 empty)

#### `boolean refreshModels()`
모델 목록을 수동으로 갱신합니다. 스케줄러를 사용하지 않을 때 직접 호출하여 갱신할 수 있습니다.

**반환값**: 갱신 성공하면 `true`

#### `boolean isModelsInitialized()`
모델 목록이 초기화(로드)되었는지 확인합니다.

**반환값**: 초기화 완료되었으면 `true`

#### `SuhAiderResponse generate(SuhAiderRequest request)`
AI 텍스트를 생성합니다 (상세 옵션 지원).

**파라미터**:
- `request`: `SuhAiderRequest` (model, prompt, stream 포함)

**반환값**: `SuhAiderResponse` (생성된 텍스트 및 메타데이터)
**예외**: `SuhAiderException`

#### `String generate(String model, String prompt)`
AI 텍스트를 생성합니다 (간편 버전).

**파라미터**:
- `model`: 모델명 (예: `"gemma3:4b"`)
- `prompt`: 프롬프트 텍스트

**반환값**: 생성된 텍스트 (`String`)
**예외**: `SuhAiderException`

#### `void generateStream(SuhAiderRequest request, StreamCallback callback)`
AI 텍스트를 스트리밍으로 생성합니다. 토큰이 생성될 때마다 콜백이 호출됩니다.

**파라미터**:
- `request`: `SuhAiderRequest` (model, prompt 필수)
- `callback`: `StreamCallback` (onNext, onComplete, onError)

> **주의**: 스트리밍 모드에서는 `responseSchema`가 무시됩니다.

#### `void generateStream(String model, String prompt, StreamCallback callback)`
스트리밍 생성 (간편 버전).

**파라미터**:
- `model`: 모델명 (예: `"gemma3:4b"`)
- `prompt`: 프롬프트 텍스트
- `callback`: 스트리밍 콜백

#### `CompletableFuture<Void> generateStreamAsync(SuhAiderRequest request, StreamCallback callback)`
비동기 스트리밍. 백그라운드 스레드에서 실행되며 Spring MVC의 `SseEmitter`와 함께 사용할 때 유용합니다.

**파라미터**:
- `request`: `SuhAiderRequest` (model, prompt 필수)
- `callback`: 스트리밍 콜백

**반환값**: `CompletableFuture<Void>` (완료 시점 추적용)

#### `CompletableFuture<Void> generateStreamAsync(String model, String prompt, StreamCallback callback)`
비동기 스트리밍 (간편 버전).

#### `ChatResponse chat(ChatRequest request)`
대화형 AI 응답 생성. 이전 대화 기록을 포함하여 컨텍스트를 유지합니다.

**파라미터**:
- `request`: `ChatRequest` (model, messages 필수)

**반환값**: `ChatResponse` (AI 응답 메시지 포함)
**예외**: `SuhAiderException`

#### `ChatResponse chat(String model, List<ChatMessage> messages)`
간편 Chat. 모델명과 메시지 목록으로 바로 응답을 받습니다.

**파라미터**:
- `model`: 모델명 (예: `"gemma3:4b"`)
- `messages`: 대화 메시지 목록

**반환값**: `ChatResponse`

#### `String chat(String model, String userMessage)`
단일 메시지 Chat. 대화 기록 없이 단일 질문에 대한 응답을 받습니다.

**파라미터**:
- `model`: 모델명
- `userMessage`: 사용자 메시지

**반환값**: AI 응답 텍스트 (`String`)

#### `String chat(String model, String systemPrompt, String userMessage)`
시스템 프롬프트 포함 Chat.

**파라미터**:
- `model`: 모델명
- `systemPrompt`: 시스템 지시문
- `userMessage`: 사용자 메시지

**반환값**: AI 응답 텍스트 (`String`)

#### `void chatStream(ChatRequest request, StreamCallback callback)`
대화형 AI 응답 생성 (스트리밍). 토큰이 생성될 때마다 콜백이 호출됩니다.

**파라미터**:
- `request`: `ChatRequest` (model, messages 필수)
- `callback`: 스트리밍 콜백

#### `void chatStream(String model, List<ChatMessage> messages, StreamCallback callback)`
간편 Chat 스트리밍.

#### `CompletableFuture<Void> chatStreamAsync(ChatRequest request, StreamCallback callback)`
비동기 Chat 스트리밍. 백그라운드 스레드에서 실행됩니다.

**반환값**: `CompletableFuture<Void>` (완료 시점 추적용)

#### `CompletableFuture<Void> chatStreamAsync(String model, List<ChatMessage> messages, StreamCallback callback)`
간편 비동기 Chat 스트리밍.

#### `FunctionResponse functionCall(FunctionRequest request)`
Function Calling 수행. FunctionGemma 등 Function Calling 지원 모델로 사용자 의도를 분류합니다.

**파라미터**:
- `request`: `FunctionRequest` (model, userText, systemPrompt, tools 필수)

**반환값**: `FunctionResponse` (toolName, arguments)
**예외**: `SuhAiderException` (파라미터 오류, 네트워크 오류)

#### `List<Double> embed(String model, String text)`
단일 텍스트 임베딩 (간편 버전).

**파라미터**:
- `model`: 임베딩 모델명 (예: `"nomic-embed-text"`)
- `text`: 임베딩할 텍스트

**반환값**: 임베딩 벡터 `List<Double>`

#### `List<List<Double>> embed(String model, List<String> texts)`
배치 임베딩. 여러 텍스트를 한 번에 임베딩합니다.

**파라미터**:
- `model`: 임베딩 모델명
- `texts`: 임베딩할 텍스트 목록

**반환값**: 각 텍스트에 대응하는 임베딩 벡터 목록

#### `EmbeddingResponse embed(EmbeddingRequest request)`
임베딩 (상세 옵션). truncate, keepAlive, dimensions 등 세부 설정 가능.

**파라미터**:
- `request`: `EmbeddingRequest` (model, input 필수)

**반환값**: `EmbeddingResponse` (임베딩 벡터 및 메타데이터)

#### `EmbeddingResponse embedWithChunking(String model, String text, ChunkingConfig config)`
청킹 + 임베딩. 긴 텍스트를 설정에 따라 분할하고 각각 임베딩합니다.

**파라미터**:
- `model`: 임베딩 모델명
- `text`: 임베딩할 텍스트 (긴 텍스트 가능)
- `config`: 청킹 설정

**반환값**: `EmbeddingResponse` (각 청크의 임베딩 벡터 포함)

#### `EmbeddingResponse embedWithChunking(String model, String text)`
청킹 + 임베딩 (설정 기반). application.yml의 `suh.aider.embedding.chunking` 설정 사용.

#### `EmbeddingResponse embedWithChunking(String text)`
기본 모델로 청킹 + 임베딩. application.yml의 기본 모델과 청킹 설정 모두 사용.

#### `boolean pullModel(String modelName)`
모델을 다운로드합니다 (동기 방식, 완료까지 블로킹).

**파라미터**:
- `modelName`: 다운로드할 모델명 (예: `"llama3.2"`)

**반환값**: 성공하면 `true`

#### `PullHandle pullModelStream(String modelName, PullCallback callback)`
모델을 다운로드합니다 (스트리밍 방식, 진행률 콜백).

**파라미터**:
- `modelName`: 다운로드할 모델명
- `callback`: 진행률/완료/에러 콜백

**반환값**: `PullHandle` (취소 및 상태 확인용)

#### `CompletableFuture<PullResult> pullModelAsync(String modelName)`
모델을 비동기로 다운로드합니다.

**반환값**: `CompletableFuture<PullResult>`

#### `CompletableFuture<List<PullResult>> pullModelsAsync(List<String> modelNames)`
여러 모델을 병렬로 다운로드합니다.

**반환값**: `CompletableFuture<List<PullResult>>`

#### `boolean deleteModel(String modelName)`
모델을 삭제합니다.

**파라미터**:
- `modelName`: 삭제할 모델명 (예: `"llama3.2"`)

**반환값**: 성공하면 `true`
**예외**: `SuhAiderException` (MODEL_DELETE_FAILED)

### DTO 클래스

#### `SuhAiderRequest`
```java
SuhAiderRequest.builder()
    .model("gemma3:4b")      // 모델명 (필수)
    .prompt("Your prompt")   // 프롬프트 (필수)
    .stream(false)           // 스트리밍 모드 (기본: false)
    .responseSchema(schema)  // JSON 응답 강제
    .build();
```

#### `JsonSchema` (v0.0.8+)
```java
// 방법 1: 간단한 스키마
JsonSchema.of("name", "string", "age", "integer")

// 방법 2: 빌더 패턴
JsonSchema.builder()
    .property("name", "string")
    .property("age", "integer")
    .required("name")
    .build()

// 방법 3: 중첩 객체
JsonSchema.builder()
    .property("user", JsonSchema.object("name", "string", "age", "integer"))
    .build()
```

#### `SuhAiderResponse`
| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | `String` | 사용된 모델명 |
| `response` | `String` | 생성된 텍스트 |
| `done` | `Boolean` | 생성 완료 여부 |
| `totalDuration` | `Long` | 전체 처리 시간 (나노초) |

#### `ChatMessage`
```java
// 팩토리 메서드 사용 (권장)
ChatMessage.system("너는 친절한 어시스턴트야");  // 시스템 지시문
ChatMessage.user("안녕하세요?");                 // 사용자 메시지
ChatMessage.assistant("안녕하세요!");            // AI 응답 (대화 기록용)
ChatMessage.tool("{\"result\": \"success\"}");   // 도구 결과

// 이미지 포함 (멀티모달 모델용)
ChatMessage.user("이 이미지에 뭐가 있어?", List.of("base64EncodedImage"));

// 빌더 패턴
ChatMessage.builder()
    .role("user")
    .content("메시지 내용")
    .build();
```

#### `ChatRequest`
```java
ChatRequest.builder()
    .model("gemma3:4b")           // 모델명 (필수)
    .messages(List.of(...))       // 대화 메시지 목록 (필수)
    .stream(false)                // 스트리밍 모드 (기본: false)
    .format("json")               // JSON 응답 포맷
    .keepAlive("5m")              // 모델 메모리 유지 시간
    .tools(List.of(...))          // Function Calling용 도구 목록
    .build();
```

#### `ChatResponse`
| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | `String` | 사용된 모델명 |
| `createdAt` | `String` | 생성 시간 (ISO 8601) |
| `message` | `ChatMessage` | AI 응답 메시지 |
| `done` | `Boolean` | 생성 완료 여부 |
| `doneReason` | `String` | 종료 이유 (stop/length/tool_calls) |
| `totalDuration` | `Long` | 전체 처리 시간 (나노초) |

**편의 메서드**:
- `getContent()`: AI 응답 텍스트 바로 추출
- `getTotalDurationMs()`: 처리 시간(ms) 반환
- `hasToolCalls()`: 도구 호출 여부 확인

#### `EmbeddingRequest`
```java
EmbeddingRequest.builder()
    .model("nomic-embed-text")  // 임베딩 모델명 (필수)
    .input("텍스트")            // String 또는 List<String> (필수)
    .truncate(true)             // 컨텍스트 초과 시 자르기 (기본: true)
    .keepAlive("5m")            // 모델 메모리 유지 시간
    .dimensions(768)            // 임베딩 차원 수 (모델 지원 시)
    .build();
```

#### `EmbeddingResponse`
| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | `String` | 사용된 모델명 |
| `embeddings` | `List<List<Double>>` | 임베딩 벡터 목록 |
| `totalDuration` | `Long` | 전체 처리 시간 (나노초) |
| `loadDuration` | `Long` | 모델 로드 시간 (나노초) |
| `promptEvalCount` | `Integer` | 프롬프트 토큰 수 |

#### `ChunkingConfig`
```java
// 방법 1: 고정 크기 청킹
ChunkingConfig.fixedSize(500, 50)  // chunkSize, overlapSize

// 방법 2: 문장 단위 청킹
ChunkingConfig.sentence(1000)       // maxChunkSize

// 방법 3: 단락 단위 청킹
ChunkingConfig.paragraph(2000)      // maxChunkSize

// 방법 4: 빌더 패턴
ChunkingConfig.builder()
    .strategy(ChunkingConfig.Strategy.FIXED_SIZE)
    .chunkSize(500)
    .overlapSize(50)
    .enabled(true)
    .build();
```

| 전략 | 설명 |
|------|------|
| `FIXED_SIZE` | 고정 문자 수로 분할 (기본값) |
| `SENTENCE` | 문장 종결자(. ! ?) 기준 분할 |
| `PARAGRAPH` | 빈 줄(\n\n) 기준 분할 |

#### `ModelInfo`
| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 모델 이름 |
| `size` | `Long` | 모델 크기 (바이트) |
| `modifiedAt` | `String` | 수정 일시 |

#### `FunctionRequest`
```java
FunctionRequest.builder()
    .model("functiongemma")        // 모델명 (필수)
    .userText("SSE 설정 어디?")     // 사용자 입력 (필수)
    .systemPrompt("...")           // 라우팅 규칙 (필수)
    .tool(FunctionTool.of(...))    // Tool 추가 (1개 이상 필수)
    .tool(FunctionTool.of(...))    // 체이닝 가능
    .options(Map.of(...))          // 모델 옵션 (선택)
    .keepAlive("5m")               // 메모리 유지 시간 (선택)
    .build();
```

#### `FunctionResponse`
| 필드 | 타입 | 설명 |
|------|------|------|
| `toolName` | `String` | 선택된 Tool 이름 |
| `arguments` | `Map<String, Object>` | Tool 인자 맵 |
| `hasToolCall` | `boolean` | Tool 호출 존재 여부 |
| `rawResponse` | `ChatResponse` | 원본 응답 (디버깅용) |

**편의 메서드**:
- `getArgumentAsString(key)`: 인자를 String으로 추출
- `getArgumentAsInteger(key)`: 인자를 Integer로 추출
- `getArgumentAsBoolean(key)`: 인자를 Boolean으로 추출
- `getArgumentAsList(key)`: 인자를 List로 추출
- `hasArgument(key)`: 특정 인자 존재 여부

#### `FunctionTool`
```java
// 방법 1: 파라미터 없는 Tool
FunctionTool.of("route_smalltalk", "Use for greetings")

// 방법 2: 단일 파라미터 Tool
FunctionTool.of("route_rag", "Use for RAG search",
    "query", "string", "Search query")

// 방법 3: 빌더 패턴
FunctionTool.builder()
    .name("route_system")
    .description("Use for system operations")
    .parameters(List.of(
        FunctionTool.FunctionParameter.required("query", "string", "Query"),
        FunctionTool.FunctionParameter.optional("limit", "integer", "Result limit"),
        FunctionTool.FunctionParameter.enumType("action", "Action type",
            "get_status", "get_logs", "check_port")
    ))
    .build();
```

#### `StreamCallback`
스트리밍 응답을 처리하기 위한 콜백 인터페이스입니다.

| 메서드 | 설명 |
|--------|------|
| `onNext(String chunk)` | 토큰이 생성될 때마다 호출됩니다 |
| `onComplete()` | 응답 생성이 완료되면 호출됩니다 |
| `onError(Throwable error)` | 에러 발생 시 호출됩니다 |

#### `PullCallback`
모델 다운로드 진행률을 처리하기 위한 콜백 인터페이스입니다.

| 메서드 | 설명 |
|--------|------|
| `onProgress(PullProgress progress)` | 진행 상태 업데이트 시 호출됩니다 |
| `onComplete(PullResult result)` | 다운로드 완료 시 호출됩니다 (성공/실패/취소) |
| `onError(Throwable error)` | 에러 발생 시 호출됩니다 |

#### `PullHandle`
진행 중인 다운로드의 상태 확인 및 취소에 사용합니다.

| 메서드 | 설명 |
|--------|------|
| `cancel()` | 다운로드를 취소합니다 |
| `isCancelled()` | 취소되었는지 확인합니다 |
| `isDone()` | 완료되었는지 확인합니다 |
| `getLatestProgress()` | 최신 진행 상태를 가져옵니다 |
| `getModelName()` | 다운로드 중인 모델명을 가져옵니다 |

#### `PullProgress`
| 필드 | 타입 | 설명 |
|------|------|------|
| `status` | `String` | 현재 상태 (pulling manifest, downloading, verifying 등) |
| `digest` | `String` | 다운로드 중인 레이어 다이제스트 |
| `completed` | `long` | 완료된 바이트 수 |
| `total` | `long` | 전체 바이트 수 |

**편의 메서드**:
- `getPercent()`: 진행률 (0.0 ~ 100.0)
- `getFormattedProgress()`: 포맷된 진행 상태 (예: "1.2 GB / 4.0 GB (30.0%)")
- `isSuccess()`: 성공 상태인지 확인

#### `PullResult`
| 필드 | 타입 | 설명 |
|------|------|------|
| `modelName` | `String` | 다운로드한 모델명 |
| `success` | `boolean` | 성공 여부 |
| `cancelled` | `boolean` | 취소 여부 |
| `totalDurationMs` | `long` | 소요 시간 (밀리초) |
| `errorMessage` | `String` | 실패 시 에러 메시지 |

**편의 메서드**:
- `getFormattedDuration()`: 포맷된 소요 시간 (예: "2분 30초")

### 예외 (SuhAiderException)

| 에러 코드 | 설명 |
|-----------|------|
| `NETWORK_ERROR` | 네트워크 연결 오류 |
| `MODEL_NOT_FOUND` | 요청한 모델을 찾을 수 없음 |
| `INVALID_PARAMETER` | 잘못된 파라미터 |
| `UNAUTHORIZED` | API 키가 올바르지 않음 (401) |
| `FORBIDDEN` | 접근 권한 없음 (403) |
| `SERVER_ERROR` | AI 서버 오류 (500/502/503) |
| `EMBEDDING_FAILED` | 임베딩 생성 실패 |
| `EMBEDDING_CONTEXT_OVERFLOW` | 입력 텍스트가 모델 컨텍스트 길이 초과 |
| `MODEL_DELETE_FAILED` | 모델 삭제 실패 |
| `MODEL_PULL_FAILED` | 모델 다운로드 실패 |
| `MODEL_PULL_CANCELLED` | 모델 다운로드 취소됨 |

> **참고**: API 키는 이제 선택적입니다. 설정하지 않으면 인증 헤더를 추가하지 않습니다.

---

## 테스트

### 테스트 환경 설정

1. **템플릿 파일 복사**:
   ```bash
   cp src/test/resources/application.yml.template src/test/resources/application.yml
   ```

2. **API 키 설정**:
   `src/test/resources/application.yml` 파일을 열어 `YOUR_API_KEY_HERE`를 실제 API 키로 변경

3. **테스트 실행**:
   ```bash
   ./gradlew test
   ```

자세한 내용은 [테스트 설정 가이드](src/test/resources/README.md)를 참고하세요.

---

## 사용 가능한 모델

AI 서버에서 제공하는 모델 예시:

| 모델명 | 크기 | 설명 |
|--------|------|------|
| `gemma3:4b` | ~3.2GB | Google Gemma 3 (4B 파라미터) |
| `gemma3:1b` | ~777MB | Google Gemma 3 (1B 파라미터, 경량) |
| `qwen3:4b` | ~2.4GB | Alibaba Qwen 3 (4B 파라미터) |
| `exaone3.5:7.8b` | ~4.5GB | LG EXAONE 3.5 (7.8B 파라미터) |

모델 목록은 `suhAiderEngine.getModels()`로 확인할 수 있습니다.

---

## 요구사항

- **Java**: 21 이상
- **Spring Boot**: 3.5.7
- **AI 서버**: 실행 중이어야 함

---

## 기여

이슈 및 풀 리퀘스트는 언제나 환영합니다!

---

## 라이선스

[LICENSE.md](LICENSE.md) 참고

---

## 문의

프로젝트 관련 문의사항은 이슈를 통해 남겨주세요.

---

<!-- 템플릿 초기화 완료: 2025-11-16 23:03:52 KST -->
