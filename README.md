# SUH-AIDER

AI 서버와 간편하게 통신할 수 있는 Spring Boot 라이브러리입니다.

<!-- 수정하지마세요 자동으로 동기화 됩니다 -->
## 최신 버전 : v0.1.3 (2025-11-19)

[전체 버전 기록 보기](CHANGELOG.md)

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [설치](#설치)
- [빠른 시작](#빠른-시작)
- [설정](#설정)
- [사용 예제](#사용-예제)
- [JSON Schema 가이드](docs/JSON_SCHEMA_GUIDE.md)
- [API 레퍼런스](#api-레퍼런스)
- [테스트](#테스트)
- [라이선스](#라이선스)

---

## 개요

**SUH-AIDER**는 AI 서버(`https://ai.suhsaechan.kr`)와의 통신을 간소화하는 Spring Boot 라이브러리입니다.

### 특징
- ✅ **Auto-Configuration**: Spring Boot 자동 설정 지원
- ✅ **간편한 API**: 직관적인 메서드로 AI 서버 통신
- ✅ **스트리밍 응답**: GPT처럼 실시간 토큰 단위 응답
- ✅ **JSON 응답 강제**: JSON Schema 기반 구조화된 응답 보장
- ✅ **OkHttp 기반**: 안정적이고 효율적인 HTTP 통신
- ✅ **타입 안전**: 완벽한 Java 타입 지원
- ✅ **예외 처리**: 명확한 에러 코드 및 메시지

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **Health Check** | AI 서버 상태 확인 |
| **모델 목록 조회** | 설치된 AI 모델 목록 가져오기 |
| **텍스트 생성 (Generate)** | AI 프롬프트로 텍스트 생성 |
| **스트리밍 응답** | GPT처럼 실시간 토큰 단위 응답 표시 |
| **JSON 응답 강제** | JSON Schema로 구조화된 응답 보장 |
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

### 7. 예외 처리

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

#### `ModelInfo`
| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 모델 이름 |
| `size` | `Long` | 모델 크기 (바이트) |
| `modifiedAt` | `String` | 수정 일시 |

#### `StreamCallback`
스트리밍 응답을 처리하기 위한 콜백 인터페이스입니다.

| 메서드 | 설명 |
|--------|------|
| `onNext(String chunk)` | 토큰이 생성될 때마다 호출됩니다 |
| `onComplete()` | 응답 생성이 완료되면 호출됩니다 |
| `onError(Throwable error)` | 에러 발생 시 호출됩니다 |

### 예외 (SuhAiderException)

| 에러 코드 | 설명 |
|-----------|------|
| `NETWORK_ERROR` | 네트워크 연결 오류 |
| `MODEL_NOT_FOUND` | 요청한 모델을 찾을 수 없음 |
| `INVALID_PARAMETER` | 잘못된 파라미터 |
| `UNAUTHORIZED` | API 키가 올바르지 않음 (401) |
| `FORBIDDEN` | 접근 권한 없음 (403) |
| `SERVER_ERROR` | AI 서버 오류 (500/502/503) |

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
