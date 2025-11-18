# JSON Schema 기능 가이드

> **v0.0.8+**: Ollama AI 응답을 JSON 형식으로 강제하는 기능

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [Bean Configuration 설정](#bean-configuration-설정)
- [JSON Schema 정의 방법](#json-schema-정의-방법)
- [사용 예제](#사용-예제)
- [고급 사용법](#고급-사용법)
- [트러블슈팅](#트러블슈팅)

---

## 개요

**문제**: Ollama 모델(gemma3:4b, gemma3:8b 등)은 자유 형식 텍스트를 반환하므로, 구조화된 JSON 응답을 얻기 어렵습니다.

**해결**: `responseSchema`를 지정하면 프롬프트에 JSON 형식 지시문이 자동으로 추가되고, 응답도 자동으로 정제됩니다.

### 작동 원리

```
사용자 프롬프트 + JsonSchema
        ↓
프롬프트 자동 증강 (PromptEnhancer)
        ↓
Ollama AI 호출
        ↓
응답 자동 정제 (JsonResponseCleaner)
        ↓
순수 JSON 반환
```

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **자동 프롬프트 증강** | JSON 형식 지시문 자동 추가 |
| **응답 자동 정제** | 마크다운 코드 블록 제거 및 JSON 추출 |
| **JSON 유효성 검증** | 응답이 유효한 JSON인지 자동 확인 |
| **전역 설정 지원** | @Bean으로 기본 스키마 설정 가능 |
| **클래스 기반 스키마** (v0.0.9+) | DTO 클래스로부터 자동 스키마 생성 ⭐ |
| **하위 호환성** | 기존 코드 100% 동작 (스키마 미지정 시) |

---

## Bean Configuration 설정

### 방법 1: 환경 설정만 사용 (기본)

```yaml
# application.yml
suh:
  ai:
    base-url: https://ai.suhsaechan.kr
    api-key: ${AI_API_KEY}
```

이 경우 `OllamaService`는 자동으로 Bean 등록되며, 요청별로 `responseSchema`를 지정할 수 있습니다.

### 방법 2: @Bean으로 전역 기본 스키마 설정

```java
@Configuration
public class AiConfig {

    /**
     * 모든 AI 요청에 기본으로 적용할 JSON 스키마 설정
     */
    @Bean
    public OllamaServiceCustomizer ollamaCustomizer() {
        return OllamaServiceCustomizer.builder()
            .defaultResponseSchema(JsonSchema.of(
                "result", "string",
                "success", "boolean"
            ))
            .build();
    }
}
```

**효과**: 이제 모든 `ollamaService.generate()` 호출에서 자동으로 `{ "result": "...", "success": true }` 형식으로 응답받습니다.

### 방법 3: 프롬프트 전처리/후처리 추가

```java
@Bean
public OllamaServiceCustomizer ollamaCustomizer() {
    return OllamaServiceCustomizer.builder()
        .defaultResponseSchema(JsonSchema.of("answer", "string"))
        .promptPrefix("[System] You are a helpful assistant.\n\n")
        .promptSuffix("\n\nPlease be concise.")
        .customReadTimeout(180)  // 3분 타임아웃
        .build();
}
```

---

## JSON Schema 정의 방법

### 1️⃣ 간단한 스키마 (정적 팩토리 메서드)

**방법**: `JsonSchema.of("필드명", "타입", "필드명", "타입", ...)`

```java
// 2개 필드
JsonSchema schema = JsonSchema.of("name", "string", "age", "integer");

// 4개 필드
JsonSchema schema = JsonSchema.of(
    "name", "string",
    "age", "integer",
    "email", "string",
    "active", "boolean"
);
```

**지원 타입**:
- `"string"` - 문자열
- `"integer"` - 정수
- `"number"` - 실수
- `"boolean"` - 불리언
- `"object"` - 객체 (중첩)
- `"array"` - 배열

### 2️⃣ 필수 필드 지정

```java
JsonSchema schema = JsonSchema.of(
    "name", "string",
    "age", "integer",
    "email", "string"
).required("name", "age");  // email은 선택적
```

### 3️⃣ 빌더 패턴 (복잡한 스키마)

```java
JsonSchema schema = JsonSchema.builder()
    .property("name", "string")
    .property("age", "integer")
    .property("email", "string")
    .property("address", "string")
    .required("name", "age")
    .build();
```

### 4️⃣ 중첩 객체

```java
JsonSchema schema = JsonSchema.builder()
    .property("user", JsonSchema.object(
        "firstName", "string",
        "lastName", "string",
        "age", "integer"
    ))
    .property("timestamp", "string")
    .required("user")
    .build();

// 응답 예시:
// {
//   "user": { "firstName": "John", "lastName": "Doe", "age": 30 },
//   "timestamp": "2025-11-17T12:00:00Z"
// }
```

### 5️⃣ 배열 스키마

**문자열 배열**:
```java
JsonSchema schema = JsonSchema.array("string");

// 응답 예시: ["apple", "banana", "cherry"]
```

**객체 배열**:
```java
JsonSchema schema = JsonSchema.arrayOf(
    JsonSchema.object("name", "string", "age", "integer")
);

// 응답 예시:
// [
//   { "name": "John", "age": 30 },
//   { "name": "Jane", "age": 25 }
// ]
```

**배열을 속성으로 포함**:
```java
JsonSchema schema = JsonSchema.builder()
    .property("users", JsonSchema.arrayOf(
        JsonSchema.object("name", "string", "email", "string")
    ))
    .property("total", "integer")
    .build();

// 응답 예시:
// {
//   "users": [
//     { "name": "John", "email": "john@example.com" },
//     { "name": "Jane", "email": "jane@example.com" }
//   ],
//   "total": 2
// }
```

### 6️⃣ 클래스 기반 스키마 (v0.0.9+) ⭐

**가장 권장하는 방식**: DTO 클래스에 어노테이션을 붙여서 자동으로 스키마 생성

#### 어노테이션 종류

| 어노테이션 | 대상 | 설명 |
|-----------|------|------|
| `@AiClass` | 클래스 | 스키마 제목 및 설명 정의 |
| `@AiSchema` | 필드 | 필드 메타데이터 (설명, 제약 조건 등) |
| `@AiHidden` | 필드 | JSON 스키마에서 제외 |
| `@AiArraySchema` | 필드 (배열) | 배열 상세 정보 |

#### 기본 사용법

```java
@AiClass(
    title = "사용자 정보",
    description = "회원가입 시 입력받는 사용자 기본 정보"
)
@Data
public class UserResponse {

    @AiSchema(
        description = "사용자 이름",
        required = true,
        minLength = 2,
        maxLength = 50,
        example = "홍길동"
    )
    private String name;

    @AiSchema(
        description = "나이",
        required = true,
        minimum = "0",
        maximum = "150",
        example = "30"
    )
    private Integer age;

    @AiSchema(
        description = "이메일 주소",
        format = "email",
        pattern = "^[A-Za-z0-9+_.-]+@(.+)$"
    )
    private String email;

    @AiHidden  // 스키마에서 제외
    private String internalId;
}

// 사용
JsonSchema schema = JsonSchema.fromClass(UserResponse.class);

OllamaRequest request = OllamaRequest.builder()
    .model("gemma3:4b")
    .prompt("Extract user info from: John Doe, 30 years old, john@example.com")
    .responseSchema(schema)
    .build();

OllamaResponse response = ollamaService.generate(request);

// JSON → DTO 자동 변환
UserResponse user = objectMapper.readValue(response.getResponse(), UserResponse.class);
```

#### 배열 필드 정의

```java
@Data
public class UserWithInterests {

    @AiSchema(description = "사용자 이름")
    private String name;

    @AiSchema(description = "관심사 목록")
    @AiArraySchema(
        itemType = String.class,
        minItems = 1,
        maxItems = 10,
        uniqueItems = true
    )
    private List<String> interests;
}

// 사용
JsonSchema schema = JsonSchema.fromClass(UserWithInterests.class);
```

#### 중첩 객체 정의

```java
@AiClass(title = "주소 정보")
@Data
public class Address {
    @AiSchema(description = "도시", required = true)
    private String city;

    @AiSchema(description = "우편번호")
    private String zipCode;
}

@AiClass(title = "사용자 상세")
@Data
public class UserDetail {
    @AiSchema(description = "이름")
    private String name;

    @AiSchema(description = "주소")
    private Address address;  // 자동으로 중첩 객체 스키마 생성
}
```

#### allowableValues (Enum 값 제한)

```java
@Data
public class Membership {

    @AiSchema(
        description = "회원 등급",
        allowableValues = {"BRONZE", "SILVER", "GOLD", "PLATINUM"},
        example = "GOLD"
    )
    private String level;
}
```

#### 타입 자동 추론

| Java 타입 | JSON 타입 |
|-----------|-----------|
| `String` | `"string"` |
| `Integer`, `Long`, `int`, `long` | `"integer"` |
| `Double`, `Float`, `BigDecimal` | `"number"` |
| `Boolean`, `boolean` | `"boolean"` |
| `List`, `Set`, `Array` | `"array"` |
| 커스텀 클래스 | `"object"` (재귀 파싱) |

**타입 명시적 지정**:
```java
@AiSchema(type = "string", description = "명시적 문자열")
private Object someField;
```

---

## 사용 예제

### 예제 1: 가장 간단한 사용 ⭐

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final OllamaService ollamaService;

    public String extractUserInfo(String text) {
        OllamaRequest request = OllamaRequest.builder()
            .model("gemma3:4b")
            .prompt("Extract name and age from: " + text)
            .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
            .build();

        OllamaResponse response = ollamaService.generate(request);
        return response.getResponse();  // { "name": "John", "age": 30 }
    }
}
```

### 예제 2: 클래스 기반 스키마 + DTO 자동 변환 (v0.0.9+) ⭐⭐⭐

```java
@AiClass(title = "사용자 정보")
@Data
public class UserInfo {
    @AiSchema(description = "이름", required = true)
    private String name;

    @AiSchema(description = "나이", required = true)
    private Integer age;
}

@Service
@RequiredArgsConstructor
public class UserService {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public UserInfo extractUserInfo(String text) throws JsonProcessingException {
        OllamaRequest request = OllamaRequest.builder()
            .model("gemma3:4b")
            .prompt("Extract name and age from: " + text)
            .responseSchema(JsonSchema.fromClass(UserInfo.class))  // 클래스 기반
            .build();

        OllamaResponse response = ollamaService.generate(request);

        // JSON → DTO 자동 변환
        return objectMapper.readValue(response.getResponse(), UserInfo.class);
    }
}
```

### 예제 3: 복잡한 정보 추출

```java
public String analyzeReview(String reviewText) {
    JsonSchema schema = JsonSchema.builder()
        .property("sentiment", "string")  // "positive", "negative", "neutral"
        .property("rating", "integer")     // 1-5
        .property("keywords", JsonSchema.array("string"))
        .property("summary", "string")
        .required("sentiment", "rating")
        .build();

    OllamaRequest request = OllamaRequest.builder()
        .model("gemma3:8b")
        .prompt("Analyze this product review: " + reviewText)
        .responseSchema(schema)
        .build();

    return ollamaService.generate(request).getResponse();

    // 응답 예시:
    // {
    //   "sentiment": "positive",
    //   "rating": 4,
    //   "keywords": ["great", "quality", "fast delivery"],
    //   "summary": "Customer is satisfied with product quality and delivery speed"
    // }
}
```

### 예제 4: 전역 스키마 오버라이드

```java
// AiConfig.java에서 전역 스키마를 설정했지만, 특정 요청에서만 다른 스키마 사용
@Bean
public OllamaServiceCustomizer ollamaCustomizer() {
    return OllamaServiceCustomizer.builder()
        .defaultResponseSchema(JsonSchema.of("result", "string"))
        .build();
}

// 서비스 코드
public String customRequest() {
    OllamaRequest request = OllamaRequest.builder()
        .model("gemma3:4b")
        .prompt("Extract user info")
        .responseSchema(JsonSchema.of("name", "string", "age", "integer"))  // 전역 스키마 무시
        .build();

    return ollamaService.generate(request).getResponse();
}
```

### 예제 5: 스키마 없이 기존 방식 사용

```java
// responseSchema를 지정하지 않으면 기존 방식 그대로 동작 (하위 호환성)
String response = ollamaService.generate("gemma3:4b", "Hello, how are you?");
// AI가 자유 형식으로 응답
```

---

## 고급 사용법

### 1. 스키마 재사용

```java
@Configuration
public class SchemaConfig {

    @Bean("userSchema")
    public JsonSchema userSchema() {
        return JsonSchema.of(
            "name", "string",
            "email", "string",
            "age", "integer"
        ).required("name", "email");
    }

    @Bean("reviewSchema")
    public JsonSchema reviewSchema() {
        return JsonSchema.builder()
            .property("rating", "integer")
            .property("comment", "string")
            .property("sentiment", "string")
            .required("rating")
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class MyService {

    @Qualifier("userSchema")
    private final JsonSchema userSchema;

    public String extractUser(String text) {
        OllamaRequest request = OllamaRequest.builder()
            .model("gemma3:4b")
            .prompt("Extract user info: " + text)
            .responseSchema(userSchema)  // Bean으로 주입받은 스키마 재사용
            .build();

        return ollamaService.generate(request).getResponse();
    }
}
```

### 2. 조건부 스키마 적용

```java
public String processRequest(String input, boolean needStructuredOutput) {
    OllamaRequest.OllamaRequestBuilder builder = OllamaRequest.builder()
        .model("gemma3:4b")
        .prompt(input);

    // 구조화된 출력이 필요한 경우에만 스키마 적용
    if (needStructuredOutput) {
        builder.responseSchema(JsonSchema.of("result", "string", "confidence", "number"));
    }

    return ollamaService.generate(builder.build()).getResponse();
}
```

### 3. 응답 검증 및 재시도

```java
public String generateWithRetry(String prompt, JsonSchema schema, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        OllamaRequest request = OllamaRequest.builder()
            .model("gemma3:4b")
            .prompt(prompt)
            .responseSchema(schema)
            .build();

        OllamaResponse response = ollamaService.generate(request);
        String json = response.getResponse();

        // JSON 유효성 검증
        if (JsonResponseCleaner.isValidJson(json)) {
            return json;
        }

        log.warn("Invalid JSON, retrying... ({}/{})", i + 1, maxRetries);
    }

    throw new RuntimeException("Failed to get valid JSON after " + maxRetries + " retries");
}
```

---

## 트러블슈팅

### Q1: AI가 JSON을 반환하지 않아요

**원인**: gemma3:4b/8b 같은 작은 모델은 복잡한 지시를 완벽히 따르지 못할 수 있습니다.

**해결책**:
1. 스키마를 더 간단하게 (3-5개 필드 권장)
2. 더 큰 모델 사용 (gemma3:8b 또는 그 이상)
3. 프롬프트를 더 명확하게 작성

### Q2: 응답에 마크다운 코드 블록이 포함돼요

**원인**: AI가 ```json ... ``` 형식으로 반환하는 경우가 있습니다.

**해결책**: `JsonResponseCleaner.clean()`이 자동으로 제거하므로 별도 처리 불필요합니다.

```java
String rawResponse = "```json\n{\"name\":\"John\"}\n```";
String cleaned = JsonResponseCleaner.clean(rawResponse);
// → {"name":"John"}
```

### Q3: 중첩 객체가 제대로 생성되지 않아요

**원인**: 너무 깊은 중첩(3단계 이상)은 작은 모델이 이해하기 어렵습니다.

**해결책**: 최대 2단계 중첩까지만 사용하거나, 더 큰 모델 사용

```java
// ❌ 너무 복잡
JsonSchema.builder()
    .property("user", JsonSchema.object(
        "profile", JsonSchema.object(
            "details", JsonSchema.object(...)  // 3단계 중첩
        )
    ))
    .build();

// ✅ 적절한 복잡도
JsonSchema.builder()
    .property("user", JsonSchema.object(
        "name", "string",
        "email", "string"
    ))
    .build();
```

### Q4: 전역 스키마가 적용되지 않아요

**확인 사항**:
1. `OllamaServiceCustomizer` Bean이 제대로 등록되었는지 확인
2. 개별 요청에서 `responseSchema`를 지정하면 전역 스키마가 무시됨

```java
// 로그 확인
// INFO - OllamaService Bean 생성 - customizer: 있음
```

### Q5: JSON 파싱 에러가 발생해요

**원인**: AI가 유효하지 않은 JSON을 반환했을 가능성

**해결책**: 로그 확인 및 재시도 로직 구현

```java
// 로그 확인
// WARN - AI가 유효하지 않은 JSON 반환 (원본 유지): {...

// 재시도 로직 (예제 3 참고)
```

---

## 📌 참고

- **구현 이슈**: [docs/feature-json-schema-support.md](./feature-json-schema-support.md)
- **버전**: v0.0.8+
- **관련 클래스**:
  - `JsonSchema` - 스키마 정의
  - `OllamaServiceCustomizer` - 전역 설정
  - `PromptEnhancer` - 프롬프트 증강
  - `JsonResponseCleaner` - 응답 정제

---

**작성일**: 2025-11-17
**최종 업데이트**: 2025-11-17
