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
- [JSON Schema 가이드](docs/JSON_SCHEMA_GUIDE.md) (v0.0.8+) ⭐
- [API 레퍼런스](#api-레퍼런스)
- [테스트](#테스트)
- [라이선스](#라이선스)

---

## 개요

**SUH-AIDER**는 AI 서버(`https://ai.suhsaechan.kr`)와의 통신을 간소화하는 Spring Boot 라이브러리입니다.

### 특징
- ✅ **Auto-Configuration**: Spring Boot 자동 설정 지원
- ✅ **간편한 API**: 직관적인 메서드로 AI 서버 통신
- ✅ **JSON 응답 강제** (v0.0.8+): JSON Schema 기반 구조화된 응답 보장
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
| **JSON 응답 강제** (v0.0.8+) | JSON Schema로 구조화된 응답 보장 |
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
    # AI 서버 기본 URL (필수)
    base-url: https://ai.suhsaechan.kr

    # Security Header 설정 (선택적)
    # 인증이 필요한 서버에서만 설정하세요
    security:
      # HTTP 헤더 이름 (기본값: X-API-Key)
      header-name: X-API-Key

      # 헤더 값 포맷 (기본값: {value})
      # {value}는 api-key 값으로 치환됩니다
      header-value-format: "{value}"

      # API 인증 키 (선택적)
      # 설정하지 않으면 인증 헤더를 추가하지 않습니다
      api-key: ${AI_API_KEY}

    # HTTP 연결 타임아웃 (초, 기본: 30)
    connect-timeout: 30

    # HTTP 읽기 타임아웃 (초, 기본: 120)
    read-timeout: 120

    # HTTP 쓰기 타임아웃 (초, 기본: 30)
    write-timeout: 30

    # Auto-Configuration 활성화 여부 (기본: true)
    enabled: true
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
ModelListResponse response = suhAiderEngine.getModels();
response.getModels().forEach(model -> {
    System.out.println("모델: " + model.getName());
    System.out.println("크기: " + model.getSize() / 1024 / 1024 + " MB");
});
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

### 6. 예외 처리

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
설치된 모델 목록을 조회합니다.

**반환값**: `ModelListResponse` (모델 목록 포함)
**예외**: `SuhAiderException`

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
