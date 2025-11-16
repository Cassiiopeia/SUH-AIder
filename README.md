# suh-ai-module

Ollama AI 서버와 간편하게 통신할 수 있는 Spring Boot 라이브러리입니다.

<!-- 수정하지마세요 자동으로 동기화 됩니다 -->
## 최신 버전 : v0.0.5 (2025-11-16)

[전체 버전 기록 보기](CHANGELOG.md)

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

---

## 개요

**suh-ai-module**은 Ollama AI 서버(`https://ai.suhsaechan.kr`)와의 통신을 간소화하는 Spring Boot 라이브러리입니다.

### 특징
- ✅ **Auto-Configuration**: Spring Boot 자동 설정 지원
- ✅ **간편한 API**: 직관적인 메서드로 AI 서버 통신
- ✅ **OkHttp 기반**: 안정적이고 효율적인 HTTP 통신
- ✅ **타입 안전**: 완벽한 Java 타입 지원
- ✅ **예외 처리**: 명확한 에러 코드 및 메시지

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **Health Check** | Ollama 서버 상태 확인 |
| **모델 목록 조회** | 설치된 AI 모델 목록 가져오기 |
| **텍스트 생성 (Generate)** | AI 프롬프트로 텍스트 생성 |
| **간편 API** | 한 줄로 AI 응답 받기 |

---

## 설치

### Gradle

```gradle
dependencies {
    implementation 'kr.suhsaechan:suh-ai-module:0.0.5'
}
```

### Maven

```xml
<dependency>
    <groupId>kr.suhsaechan</groupId>
    <artifactId>suh-ai-module</artifactId>
    <version>0.0.5</version>
</dependency>
```

---

## 빠른 시작

### 1. 설정 파일 작성

`src/main/resources/application.yml`:

```yaml
suh:
  ai:
    base-url: https://ai.suhsaechan.kr
    api-key: ${AI_API_KEY}  # 환경변수 사용 권장
```

### 2. 서비스 주입 및 사용

```java
import kr.suhsaechan.ai.service.OllamaService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyService {

    private final OllamaService ollamaService;

    public void example() {
        // 간편 사용
        String response = ollamaService.generate("gemma3:4b", "Hello, AI!");
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
  ai:
    # Ollama 서버 기본 URL (필수)
    base-url: https://ai.suhsaechan.kr

    # API 인증 키 (필수)
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
boolean isHealthy = ollamaService.isHealthy();
if (isHealthy) {
    System.out.println("서버 정상 작동 중");
}
```

### 2. 모델 목록 조회

```java
ModelListResponse response = ollamaService.getModels();
response.getModels().forEach(model -> {
    System.out.println("모델: " + model.getName());
    System.out.println("크기: " + model.getSize() / 1024 / 1024 + " MB");
});
```

### 3. AI 텍스트 생성 (간편)

```java
String response = ollamaService.generate(
    "gemma3:4b",  // 모델명
    "Explain quantum computing in one sentence."  // 프롬프트
);
System.out.println(response);
```

### 4. AI 텍스트 생성 (상세)

```java
OllamaRequest request = OllamaRequest.builder()
    .model("gemma3:4b")
    .prompt("Write a haiku about coding.")
    .stream(false)
    .build();

OllamaResponse response = ollamaService.generate(request);

System.out.println("응답: " + response.getResponse());
System.out.println("처리 시간: " + response.getTotalDuration() / 1_000_000 + " ms");
```

### 5. 예외 처리

```java
try {
    String response = ollamaService.generate("invalid-model", "Hello");
} catch (OllamaException e) {
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

### OllamaService

#### `boolean isHealthy()`
Ollama 서버의 상태를 확인합니다.

**반환값**: 서버가 정상이면 `true`, 아니면 `false`

#### `ModelListResponse getModels()`
설치된 모델 목록을 조회합니다.

**반환값**: `ModelListResponse` (모델 목록 포함)
**예외**: `OllamaException`

#### `OllamaResponse generate(OllamaRequest request)`
AI 텍스트를 생성합니다 (상세 옵션 지원).

**파라미터**:
- `request`: `OllamaRequest` (model, prompt, stream 포함)

**반환값**: `OllamaResponse` (생성된 텍스트 및 메타데이터)
**예외**: `OllamaException`

#### `String generate(String model, String prompt)`
AI 텍스트를 생성합니다 (간편 버전).

**파라미터**:
- `model`: 모델명 (예: `"gemma3:4b"`)
- `prompt`: 프롬프트 텍스트

**반환값**: 생성된 텍스트 (`String`)
**예외**: `OllamaException`

### DTO 클래스

#### `OllamaRequest`
```java
OllamaRequest.builder()
    .model("gemma3:4b")      // 모델명 (필수)
    .prompt("Your prompt")   // 프롬프트 (필수)
    .stream(false)           // 스트리밍 모드 (기본: false)
    .build();
```

#### `OllamaResponse`
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

### 예외 (OllamaException)

| 에러 코드 | 설명 |
|-----------|------|
| `API_KEY_MISSING` | API 키가 설정되지 않음 |
| `NETWORK_ERROR` | 네트워크 연결 오류 |
| `MODEL_NOT_FOUND` | 요청한 모델을 찾을 수 없음 |
| `INVALID_PARAMETER` | 잘못된 파라미터 |
| `UNAUTHORIZED` | API 키가 올바르지 않음 |
| `SERVER_ERROR` | AI 서버 오류 |

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

Ollama 서버에서 제공하는 모델 예시:

| 모델명 | 크기 | 설명 |
|--------|------|------|
| `gemma3:4b` | ~3.2GB | Google Gemma 3 (4B 파라미터) |
| `gemma3:1b` | ~777MB | Google Gemma 3 (1B 파라미터, 경량) |
| `qwen3:4b` | ~2.4GB | Alibaba Qwen 3 (4B 파라미터) |
| `exaone3.5:7.8b` | ~4.5GB | LG EXAONE 3.5 (7.8B 파라미터) |

모델 목록은 `ollamaService.getModels()`로 확인할 수 있습니다.

---

## 요구사항

- **Java**: 21 이상
- **Spring Boot**: 3.5.7
- **Ollama 서버**: 실행 중이어야 함

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
