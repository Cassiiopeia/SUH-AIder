# SUH-AIder v2.0 설계서

- 작성일: 2026-07-27
- 대상: `kr.suhsaechan:suh-aider` (현재 v1.1.2)
- 성격: 브레이킹 체인지 허용 메이저 업그레이드

## 1. 배경

v1.x는 기능을 빠르게 붙이며 성장했고, 그 과정에서 다음이 누적됐다.

- `SuhAiderEngine` 단일 클래스 1,917줄. 이 중 약 700줄이 HTTP 호출 복붙
- Ollama가 구조화 출력(`format`)을 지원하기 전에 만든 프롬프트 증강 방식이 `generate()`에만 남아, 같은 `responseSchema` 필드가 경로마다 다르게 동작
- 문서에는 있으나 실제로는 아무 동작도 하지 않는 API
- 라이브러리인데 `@SpringBootApplication`과 `application.yml`을 배포 산출물에 포함
- 유닛 테스트가 3개뿐이고 나머지는 실서버 의존이라 CI에서 신뢰할 수 없음

v2.0의 두 축은 **사용성(API 정합성)** 과 **테스트성(네트워크 없이 검증 가능)** 이다.
Ollama 서버 운영·관리는 별도 프로젝트(`suh-ai-server`)의 책임이며, 이 라이브러리는
서버를 **쓰는 쪽**의 경험에 집중한다.

## 2. 실서버 검증으로 확정한 전제

운영 중인 Ollama 서버 대상 실측(2026-07-27):

| 항목 | 결과 |
|---|---|
| `/api/generate` + `format`(JSON Schema) | 정상. 마크다운 없는 순수 JSON 반환 |
| `/api/generate` + `format` + `stream:true` | 정상. 청크 조립 시 유효 JSON |
| `/api/embed` 배치 | 정상 (768차원) |
| `functiongemma:latest` Function Calling | **tool_calls 미생성**. 평문 거절 응답 |
| `gemma4:e2b` Function Calling | 정상. `arguments`가 JSON 객체로 반환 |
| tool_call 응답 구조 | `id` 필드 존재 (현재 모델 클래스에 없어 유실) |

→ `PromptEnhancer` 방식은 불필요함이 실측으로 확인됐다. 네이티브 `format`으로 통일한다.

라이브러리의 `UNAUTHORIZED`/`FORBIDDEN` 매핑은 인증을 요구하는 배포 환경에 대비해 유지한다.

## 3. 아키텍처

### 패키지 구조

```
kr.suhsaechan.ai
├── config
│   ├── SuhAiderConfig             설정 프로퍼티 (baseUrl 정규화·검증 포함)
│   ├── SuhAiderClientConfig       Bean 등록 일원화 (자동설정 진입점)
│   ├── SuhAiderExecutors          전용 스레드풀
│   └── SuhAiderCustomizer         전역 기본값 (defaultResponseSchema만)
├── http
│   ├── SuhAiderHttpExecutor       요청 빌드·실행·응답 역직렬화 단일 지점
│   ├── NdjsonStreamReader         NDJSON 스트림 파싱, 종료 통지 1회 보장
│   └── HttpErrorMapper            HTTP 상태코드 → SuhAiderErrorCode
├── api
│   ├── ModelApi        목록 조회·캐시·삭제
│   ├── GenerateApi     generate / generateStream
│   ├── ChatApi         chat / chatStream
│   ├── EmbeddingApi    embed / embedWithChunking
│   ├── PullApi         pull 동기·스트림·비동기·병렬
│   └── FunctionApi     functionCall
├── service
│   └── SuhAiderEngine  위임 파사드 (기존 호출 문법 유지)
├── model, util, annotation, exception   (기존 유지 + 개별 수정)
```

### 데이터 흐름

```
소비자 → SuhAiderEngine (파사드)
          → XxxApi (도메인 로직·검증·기본값)
            → SuhAiderHttpExecutor (요청 조립·실행·에러 변환)
              → OkHttpClient → Ollama
```

스트리밍 경로는 `SuhAiderHttpExecutor`가 응답을 열고 `NdjsonStreamReader`가
줄 단위 파싱과 종료 통지를 책임진다.

### 경계 원칙

- `SuhAiderHttpExecutor`는 Ollama 도메인을 모른다. URL·본문·응답 타입만 받는다.
- `XxxApi`는 OkHttp를 모른다. `SuhAiderHttpExecutor`만 의존한다.
- `SuhAiderEngine`은 로직을 갖지 않는다. 위임만 한다.

각 `Api`는 Bean으로 노출한다. 소비자가 `ChatApi`만 주입받아 테스트에서 대체할 수 있다.

## 4. 동시성 설계

### 모델 캐시

`availableModels`를 `volatile List<ModelInfo>`로 두고 **불변 스냅샷 교체** 방식으로 갱신한다.
갱신은 새 리스트를 만들어 참조를 바꾸고, 조회는 그 참조를 그대로 반환한다.

- 읽는 쪽은 락이 필요 없고, 반환된 리스트는 절대 변하지 않으므로 `ConcurrentModificationException`이 발생할 수 없다.
- 기존 구현은 `Collections.unmodifiableList(가변리스트)` 뷰를 반환해, pull 완료 스레드가
  캐시에 모델을 추가하는 순간 소비자의 순회가 깨질 수 있었다.

### 스레드풀

`SuhAiderExecutors`가 데몬 스레드 기반 전용 풀을 제공한다. 이름은 `suh-aider-*`.

- 크기: `suh.aider.async.pool-size` (기본 4)
- pull·스트리밍의 모든 비동기 실행이 이 풀을 쓴다.
- 기존에는 `CompletableFuture.runAsync()`가 공용 `ForkJoinPool`을 썼다. 수 시간짜리
  다운로드를 공용 풀에 올리면 소비자 앱 전체의 병렬 스트림·비동기 작업이 굶는다.

### 스트림 종료 보장

`NdjsonStreamReader`가 스트림 처리 전체를 `try/catch(Throwable)/finally`로 감싸고,
종료 통지가 정확히 한 번만 나가도록 `AtomicBoolean` 가드를 둔다.

기존 pull 루프는 `IOException`만 잡았기 때문에 그 외 런타임 예외가 나면 완료·에러 통지가
모두 누락되고, 이를 기다리던 `pullModel()`의 `latch.await()`가 영구 블로킹됐다.

### 동기 대기 타임아웃

`pullModel()`의 무기한 대기에 `suh.aider.pull.timeout`(기본 60분)을 적용한다.

## 5. API 변경 (브레이킹)

### 5.1 responseSchema 통일

네 경로 모두 Ollama 네이티브 `format`으로 스키마를 전달한다.

| 메서드 | v1.x | v2.0 |
|---|---|---|
| `generate()` | 프롬프트에 영어 지시문 증강 + 응답 정제 | `format` 전달 + 방어적 정제 |
| `chat()` | `format` 전달 | 동일 |
| `generateStream()` | **무시** (경고 로그) | `format` 전달 |
| `chatStream()` | 미지원 | `format` 전달 |

- `PromptEnhancer` 삭제. 프롬프트를 라이브러리가 조작하지 않는다.
- `JsonResponseCleaner`는 유지한다. 모델이 규약을 어기고 마크다운으로 감싸 보내는 경우가
  있어 방어선으로 남긴다. 단 정적 `ObjectMapper` 보유를 없애고 주입받은 매퍼를 쓴다.

**영향**: 프롬프트 내용이 바뀌므로 동일 입력에 대한 모델 출력이 v1.x와 달라질 수 있다.

### 5.2 PullCallback 종료 계약 단일화

```java
public interface PullCallback {
    void onProgress(PullProgress progress);   // 0..n회
    void onComplete(PullResult result);       // 정확히 1회. 성공·취소·실패 모두 여기로
}
```

`onError`를 제거한다. 실패 정보는 `PullResult`가 전부 담는다.

- `PullResult.getCause()` 추가 (`Throwable`, 실패 시에만 존재)
- `PullResult.failure(modelName, message, cause)` 오버로드 추가

**근거**: v1.x는 HTTP 오류는 `onError`, 응답 본문의 `error` 필드는 `onComplete(failure)`로
보내 같은 "실패"가 두 경로로 갈렸다. 소비자가 `onComplete`에서만 결과를 받는 코드를 쓰면
`onError` 경로에서 결과가 비어 NPE가 난다. 실제로 `PullIntegrationTest`가 이 형태로 실패했다.
종료 지점을 하나로 만들면 이 유형의 버그가 구조적으로 불가능해진다.

### 5.3 제거되는 API

| 대상 | 사유 |
|---|---|
| `SuhAiderCustomizer.customReadTimeout` | 엔진이 읽지 않음 (사용처 0) |
| `SuhAiderCustomizer.promptPrefix` | 엔진이 읽지 않음 (사용처 0) |
| `SuhAiderCustomizer.promptSuffix` | 엔진이 읽지 않음 (사용처 0) |
| `PromptEnhancer` (클래스 전체) | 5.1로 불필요 |
| `JsonResponseCleaner.isValidJson(String)` | 정적 매퍼 사용 오버로드. 매퍼 주입형만 유지 |
| `PullCallback.onError` | 5.2 |

### 5.4 SuhAiderErrorCode 정리

제거: `API_KEY_MISSING`, `EMBEDDING_FAILED`, `EMBEDDING_CONTEXT_OVERFLOW` (사용처 0)

변경:
- `CONNECTION_TIMEOUT` + `READ_TIMEOUT` → `TIMEOUT` 단일화.
  OkHttp는 연결·읽기 타임아웃 모두 `SocketTimeoutException`을 던져 신뢰성 있게 구분할 수 없다.
  메시지로 추측해 잘못 분류하느니 하나로 합치는 편이 정직하다.
- `CONNECTION_FAILED` 신설. `ConnectException`·`UnknownHostException` 매핑.
- `BASE_URL_INVALID` 유지하되 **실제 사용**한다 (시작 시 검증).

### 5.5 기타 정합성 수정

- `getModelInfo()`: 캐시 미초기화 시의 의미를 `isModelAvailable()`과 일치시킨다.
  두 메서드 모두 "캐시가 없으면 판단하지 않는다"로 통일하고, 판단 불가 상태를 문서화한다.
- `ChatMessage.ToolCall`에 `id` 필드 추가. 서버가 주는 값을 유실하지 않는다.
- `PullResult.getFormattedDuration()`은 `FormatUtils.formatDuration()`에 위임한다 (복붙 제거).
- `createDummyHandle()` 제거. 검증 실패 시 이미 완료된 실패 `PullHandle`을 명시적으로 반환한다.

## 6. 설정·패키징

### 삭제 대상

- `src/main/java/kr/suhsaechan/ai/AiApplication.java`
  라이브러리 산출물에 `@SpringBootApplication`이 실려 소비자 앱의 컴포넌트 스캔·자동설정에 개입할 수 있다.
- `src/main/resources/application.yml`
  prefix가 `suh.ai`로 실제 바인딩(`suh.aider`)과 불일치해 아무 효과도 없는 죽은 파일이며,
  라이브러리 jar가 소비자 클래스패스에 `application.yml`을 실어 나르는 것 자체가 위험하다.
  내용은 README의 설정 예시로 대체한다.

### 변경

- `SuhAiderEngine`의 `@Service` 제거. Bean 등록은 `SuhAiderClientConfig` 한 곳에서만.
  (클래스명은 자동설정 imports 파일이 참조하고 있어 v1.x 이름을 유지한다.)
- `SuhAiderSchedulerConfig`의 `@EnableScheduling` 제거. 자체 `ScheduledExecutorService`와
  Spring `CronExpression`으로 갱신 주기를 돌린다. 소비자 앱의 스케줄링 인프라를 건드리지 않는다.
- `baseUrl` 정규화: 후행 슬래시 제거. 현재는 `https://host/` 설정 시 모든 URL이 `//api/tags`가 된다.
- `baseUrl` 검증: 시작 시 형식 확인, 실패하면 `BASE_URL_INVALID`.
- `EmbeddingRequest.truncate`의 `@Builder.Default` 제거.
  기본값이 박혀 있어 절대 null이 아니므로 `suh.aider.embedding.truncate` 설정이 반영되지 않았다.

### 신설 설정 키

```yaml
suh:
  aider:
    async:
      pool-size: 4          # 비동기/스트리밍 전용 스레드풀 크기
    pull:
      timeout: 60m          # 동기 pullModel() 최대 대기 시간
```

## 7. 유틸 버그 수정

`JsonSchemaClassParser`가 `getDeclaredFields()`를 걸러내지 않아 **합성 필드와 static 필드를
스키마 속성으로 포함**한다.

- 메서드 내부 로컬 클래스·비정적 내부 클래스는 컴파일러가 `this$0`을 넣는다.
- `serialVersionUID`, 상수 등 static 필드도 그대로 포함된다.

결과적으로 AI에게 존재하지 않는 필드를 요구하는 스키마가 전달된다. 실제로
`JsonSchemaClassParserTest`의 개수 단언 3건이 이 때문에 실패 중이다.

수정: `field.isSynthetic()` 또는 `Modifier.isStatic(...)`인 필드를 건너뛴다.

## 8. 테스트 전략

### 유닛 테스트 (네트워크 없음, CI 필수 통과)

OkHttp `MockWebServer`로 Ollama 응답을 흉내 내어 다음을 검증한다.

| 대상 | 검증 내용 |
|---|---|
| `HttpErrorMapper` | 401/403/404/5xx/기타 → 에러코드 매핑 |
| `SuhAiderHttpExecutor` | 빈 응답, 잘못된 JSON, 타임아웃, 연결 실패 |
| `NdjsonStreamReader` | 정상 종료, 중간 파싱 실패 건너뜀, 예외 발생 시에도 종료 통지 1회 |
| `GenerateApi`/`ChatApi` | `format` 직렬화, 응답 정제, 파라미터 검증 |
| `EmbeddingApi` | 기본값 적용(특히 `truncate` 설정 반영), 청킹 연동 |
| `PullApi` | 진행률 파싱, 취소, 실패 시 `onComplete` 1회 |
| `ModelApi` | 캐시 스냅샷 불변성, 삭제 후 캐시 반영 |
| `JsonSchemaClassParser` | 합성·static 필드 제외 |
| `SuhAiderConfig` | baseUrl 정규화·검증 |

### 통합 테스트 (실서버, 선택 실행)

- `@Tag("integration")` 부여
- 환경변수 `SUH_AIDER_IT=true`일 때만 실행
- Gradle `integrationTest` 태스크로 분리. `./gradlew test`는 유닛만 돈다.
- 테스트 모델 상수를 `TestModels`로 중앙화하고 서버에 실재하는 모델로 맞춘다.
  (`qwen3-vl:2b`, `granite4:350m`은 서버에 없어 현재 실패 원인)
- `AiApplicationTests`가 컨텍스트 초기화 시 실서버로 나가지 않도록 한다.

## 9. 문서·배포

- README(1,429줄), `docs/` 5종(2,795줄)을 v2.0 API에 맞춰 갱신
- `MIGRATION-v2.md` 신설: 제거된 API와 대체 방법, 출력 변화 주의사항
- Function Calling 문서의 예제 모델을 실제 tool_calls를 생성하는 모델로 교체
  (`functiongemma:latest`는 실측상 tool_calls를 만들지 않음)
- `version.yml` → `2.0.0`, `build.gradle` 동기화
- 배포는 deploy 브랜치 트리거로 진행

## 10. 범위에서 제외한 것

- Ollama 서버 운영·관리 기능 확장 (`suh-ai-server`의 책임)
- 모델 pull/delete 기능 제거 — 최근 추가된 기능이므로 유지하되 신규 투자는 하지 않는다
- 네임스페이스 API(`engine.chat().send()`) 도입 — 마이그레이션 비용 대비 효용이 낮다고 판단
