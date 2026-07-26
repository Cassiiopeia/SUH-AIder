# v1.x → v2.0 마이그레이션 가이드

v2.0은 메이저 업그레이드입니다. **호출 문법은 대부분 그대로**지만, 아래 항목은 코드 수정이 필요합니다.

---

## 한눈에 보기

| 영향 | 항목 | 조치 |
|------|------|------|
| 🔴 컴파일 오류 | `PullCallback.onError` 제거 | `onComplete(PullResult)`로 통합 |
| 🔴 컴파일 오류 | `SuhAiderCustomizer` 필드 3개 제거 | 아래 대체 방법 참고 |
| 🔴 컴파일 오류 | `SuhAiderErrorCode` 항목 정리 | 새 코드로 교체 |
| 🔴 컴파일 오류 | `PromptEnhancer` 제거 | 삭제 (더 이상 불필요) |
| 🟡 동작 변화 | `responseSchema` 처리 방식 변경 | 응답 내용이 달라질 수 있음 |
| 🟡 동작 변화 | 스트리밍이 스키마를 지원 | 기존엔 무시됐음 |
| 🟢 무영향 | `SuhAiderEngine` 메서드 시그니처 | 그대로 |

---

## 1. PullCallback: `onError` 제거

### 변경 이유

v1.x는 같은 "실패"가 두 경로로 갈렸습니다.

- HTTP 오류(500 등) → `onError(Throwable)`
- 응답 본문의 `error` 필드 → `onComplete(PullResult.failure(...))`

`onComplete`에서만 결과를 꺼내는 코드는 `onError` 경로에서 결과가 비어 NPE가 났습니다.
v2.0은 **종료 지점을 `onComplete` 하나로 통일**해 이 유형의 버그를 구조적으로 없앴습니다.

### Before

```java
engine.pullModelStream("llama3.2", new PullCallback() {
    @Override
    public void onProgress(PullProgress progress) { ... }

    @Override
    public void onComplete(PullResult result) {
        if (result.isSuccess()) { ... }
    }

    @Override
    public void onError(Throwable error) {        // ❌ 제거됨
        log.error("실패", error);
    }
});
```

### After

```java
engine.pullModelStream("llama3.2", new PullCallback() {
    @Override
    public void onProgress(PullProgress progress) { ... }

    @Override
    public void onComplete(PullResult result) {   // ✅ 성공/실패/취소 모두 여기로
        if (result.isSuccess()) {
            log.info("완료: {}", result.getFormattedDuration());
        } else if (result.isCancelled()) {
            log.info("취소됨");
        } else {
            log.error("실패: {}", result.getErrorMessage(), result.getCause());
        }
    }
});
```

`PullResult`에 `getCause()`(`Throwable`)가 추가돼 예외 객체를 그대로 받을 수 있습니다.

### 비동기 API 동작 변화

`pullModelAsync()`는 이제 **실패도 예외가 아닌 결과로** 전달합니다.

```java
// Before: 실패 시 future.get()이 ExecutionException을 던졌음
// After : 항상 정상 완료되고 결과로 판별
PullResult result = engine.pullModelAsync("llama3.2").get();
if (!result.isSuccess()) {
    log.error(result.getErrorMessage());
}
```

동기 `pullModel()`은 그대로 실패 시 `SuhAiderException`을 던집니다.

---

## 2. SuhAiderCustomizer: 동작하지 않던 필드 3개 제거

`customReadTimeout`, `promptPrefix`, `promptSuffix`는 **엔진이 읽지 않아 설정해도 아무 효과가 없었습니다.**
헷갈릴 여지를 없애기 위해 제거했습니다.

### Before

```java
SuhAiderCustomizer.builder()
    .defaultResponseSchema(schema)
    .customReadTimeout(300)     // ❌ 실제로 동작 안 했음
    .promptPrefix("항상 한국어로 ")  // ❌ 실제로 동작 안 했음
    .promptSuffix(" 짧게 답해줘")    // ❌ 실제로 동작 안 했음
    .build();
```

### After

```java
// 타임아웃은 설정 파일로
// application.yml:
//   suh:
//     aider:
//       read-timeout: 300

SuhAiderCustomizer.builder()
    .defaultResponseSchema(schema)
    .build();
```

프롬프트 접두/접미가 필요하면 호출부에서 직접 조합하거나, Chat API의 시스템 메시지를 쓰세요.

```java
engine.chat(model, "항상 한국어로 짧게 답해줘", userMessage);
```

---

## 3. SuhAiderErrorCode 정리

### 제거된 코드

| 제거 | 사유 |
|------|------|
| `API_KEY_MISSING` | 사용처 없음 |
| `EMBEDDING_FAILED` | 사용처 없음 |
| `EMBEDDING_CONTEXT_OVERFLOW` | 사용처 없음 |
| `CONNECTION_TIMEOUT` | `TIMEOUT`으로 통합 |
| `READ_TIMEOUT` | `TIMEOUT`으로 통합 |

### 추가된 코드

| 추가 | 의미 |
|------|------|
| `TIMEOUT` | 연결·읽기 타임아웃 통합 |
| `CONNECTION_FAILED` | 연결 거부, 호스트 미해석 |
| `MODEL_PULL_TIMEOUT` | 동기 다운로드 대기 시간 초과 |

**타임아웃을 통합한 이유:** OkHttp는 연결 타임아웃과 읽기 타임아웃을 모두
`SocketTimeoutException`으로 던집니다. 신뢰성 있게 구분할 수 없으므로 메시지 문자열로
추측해 잘못 분류하는 대신 하나로 합쳤습니다.

```java
// Before
catch (SuhAiderException e) {
    if (e.getErrorCode() == SuhAiderErrorCode.READ_TIMEOUT) { ... }
}

// After
catch (SuhAiderException e) {
    if (e.getErrorCode() == SuhAiderErrorCode.TIMEOUT) { ... }
}
```

---

## 4. responseSchema 처리 방식 변경 (동작 변화 주의)

### 무엇이 바뀌었나

v1.x의 `generate()`는 Ollama가 구조화 출력을 지원하기 전 방식이었습니다.
프롬프트 앞에 영어 지시문을 덧붙이고, 응답에서 JSON을 긁어내는 방식이었죠.

v2.0은 **Ollama 네이티브 `format` 파라미터**로 스키마를 전달합니다.

| 경로 | v1.x | v2.0 |
|------|------|------|
| `generate()` | 프롬프트 증강 + 응답 정제 | `format` 전달 + 방어적 정제 |
| `chat()` | `format` 전달 | 동일 |
| `generateStream()` | **무시** (경고 로그만) | `format` 전달 |
| `chatStream()` | 미지원 | `format` 전달 |

### 코드 변경은 없음

```java
// v1.x, v2.0 동일한 코드
SuhAiderResponse response = engine.generate(SuhAiderRequest.builder()
    .model("gemma4:e2b")
    .prompt("홍길동은 30살이다. 이름과 나이를 뽑아줘.")
    .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
    .build());
```

### ⚠️ 하지만 결과가 달라질 수 있습니다

프롬프트에 영어 지시문이 더 이상 붙지 않으므로 **모델에 전달되는 입력이 바뀝니다.**
같은 프롬프트라도 v1.x와 다른 응답이 나올 수 있으니, 출력 형식에 의존하는 로직이 있다면
업그레이드 후 한 번 확인하세요.

`JsonResponseCleaner`는 방어선으로 남아 있어 모델이 마크다운으로 감싸 보내도 정제됩니다.

### 스트리밍에서 스키마가 동작합니다

```java
// v1.x: 경고만 찍히고 무시됨
// v2.0: format이 전달되어 조각을 모두 이어 붙이면 유효한 JSON
engine.generateStream(SuhAiderRequest.builder()
    .model("gemma4:e2b")
    .prompt("이름과 나이를 뽑아줘")
    .responseSchema(JsonSchema.of("name", "string", "age", "integer"))
    .build(), callback);
```

조각 단위로는 JSON이 아니므로, 완성된 JSON이 필요하면 `onComplete`에서 조립하세요.

---

## 5. PromptEnhancer 제거

`kr.suhsaechan.ai.util.PromptEnhancer` 클래스가 제거됐습니다.
네이티브 `format`으로 대체되어 더 이상 필요하지 않습니다. 직접 호출하던 코드가 있다면 삭제하세요.

`JsonResponseCleaner.isValidJson(String)` 오버로드도 제거됐습니다.
`isValidJson(String, ObjectMapper)`를 사용하세요. `prettify()`도 제거됐습니다.

---

## 6. FunctionResponse.fromChatResponse 시그니처 변경

클래스마다 자체 `ObjectMapper`를 들고 있으면 설정이 갈려 같은 응답이 다르게 해석됩니다.

```java
// Before
FunctionResponse.fromChatResponse(chatResponse);

// After
FunctionResponse.fromChatResponse(chatResponse, objectMapper);
```

일반적으로는 `engine.functionCall(request)`를 쓰므로 직접 호출할 일은 없습니다.

---

## 7. 새로 추가된 설정

```yaml
suh:
  aider:
    async:
      pool-size: 4          # 비동기/스트리밍 전용 스레드풀 크기 (기본 4)
    pull:
      timeout: 60m          # 동기 pullModel() 최대 대기 시간 (기본 60분)
```

**왜 필요한가:** v1.x는 다운로드·스트리밍을 공용 `ForkJoinPool`에 올렸습니다.
공용 풀은 CPU 코어 수 - 1 크기라, 수십 분짜리 다운로드 몇 개면 애플리케이션 전체의
병렬 스트림과 비동기 작업이 굶습니다. v2.0은 전용 풀로 격리합니다.

동기 `pullModel()`도 v1.x에서는 무제한 대기라 서버가 응답을 멈추면 스레드가 영원히
묶였습니다. 이제 `pull.timeout`을 넘기면 `MODEL_PULL_TIMEOUT`으로 실패합니다.

---

## 8. 설정 반영 버그 수정

`suh.aider.embedding.truncate` 설정이 **v1.x에서는 반영되지 않았습니다.**
DTO에 `@Builder.Default = true`가 박혀 있어 값이 절대 null이 아니었고,
"null이면 설정값 사용" 분기가 항상 거짓이었기 때문입니다.

v2.0에서 정상 동작합니다. `truncate: false`로 두셨다면 **이제 실제로 적용**되므로,
컨텍스트 초과 시 자르는 대신 에러가 반환됩니다.

---

## 9. 패키징 변화

배포 jar에서 아래가 제거됐습니다. 소비자 애플리케이션에는 영향이 없거나 개선입니다.

- `kr.suhsaechan.ai.AiApplication` — 라이브러리에 `@SpringBootApplication`이 실려 있었습니다
- `application.yml` — prefix가 `suh.ai`로 잘못돼 아무 효과도 없던 파일이며,
  라이브러리 jar가 소비자 클래스패스에 `application.yml`을 실어 나르는 것 자체가 위험했습니다
- `@EnableScheduling` — 모델 자동 갱신이 소비자 앱의 스케줄링 인프라를 켜지 않습니다

---

## 10. 새로 쓸 수 있는 것

### 도메인 API 개별 주입

`SuhAiderEngine` 전체가 아니라 필요한 API만 주입받을 수 있습니다. 테스트에서 대체하기 쉽습니다.

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final ChatApi chatApi;     // ✅ 필요한 것만

    public String ask(String q) {
        return chatApi.chat("gemma4:e2b", q);
    }
}
```

사용 가능한 Bean: `ModelApi`, `GenerateApi`, `ChatApi`, `EmbeddingApi`, `PullApi`, `FunctionApi`

### tool_call ID

`ChatMessage.ToolCall.getId()`가 추가됐습니다. v1.x는 서버가 준 `id`를 버렸습니다.
멀티턴 도구 대화에서 호출과 결과를 짝지을 때 사용하세요.

### baseUrl 정규화·검증

후행 슬래시가 자동 제거됩니다. `https://host/`로 설정해도 `//api/tags` 문제가 없습니다.
잘못된 URL은 애플리케이션 시작 시점에 `BASE_URL_INVALID`로 즉시 실패합니다.

---

## 업그레이드 체크리스트

- [ ] `PullCallback` 구현체에서 `onError` 제거하고 `onComplete`에서 실패 처리
- [ ] `SuhAiderCustomizer`의 `customReadTimeout`/`promptPrefix`/`promptSuffix` 사용처 제거
- [ ] `READ_TIMEOUT`/`CONNECTION_TIMEOUT` 참조를 `TIMEOUT`으로 교체
- [ ] `PromptEnhancer` 직접 호출 제거
- [ ] `pullModelAsync()` 실패 처리를 예외 catch → 결과 판별로 변경
- [ ] 구조화 출력 사용 중이라면 응답 형식 재확인
- [ ] `embedding.truncate: false` 설정 중이라면 동작 변화 확인
