# 모델 관리 기능 가이드

> **v1.0.0+**: Ollama 모델 다운로드(Pull) 및 삭제(Delete) API 지원

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [빠른 시작](#빠른-시작)
- [모델 다운로드 (Pull)](#모델-다운로드-pull)
- [모델 삭제 (Delete)](#모델-삭제-delete)
- [고급 사용법](#고급-사용법)
- [트러블슈팅](#트러블슈팅)

---

## 개요

SUH-AIDER는 Ollama 모델의 다운로드와 삭제를 지원합니다. 다양한 방식(동기, 비동기, 스트리밍, 병렬)으로 모델을 관리할 수 있습니다.

### 지원 기능

| 기능 | 설명 |
|------|------|
| **동기 다운로드** | 완료까지 블로킹, 간단한 사용 |
| **스트리밍 다운로드** | 실시간 진행률 콜백, 중간 취소 가능 |
| **비동기 다운로드** | CompletableFuture 반환, 논블로킹 |
| **병렬 다운로드** | 여러 모델 동시 다운로드 |
| **모델 삭제** | 설치된 모델 삭제 |

---

## 주요 기능

| 메서드 | 설명 |
|--------|------|
| `pullModel(String)` | 동기 방식, 완료까지 블로킹 |
| `pullModelStream(String, PullCallback)` | 스트리밍 방식, 진행률 콜백 + 취소 가능 |
| `pullModelAsync(String)` | 비동기 방식, CompletableFuture 반환 |
| `pullModelsParallel(List, PullCallback)` | 병렬 다운로드, 여러 모델 동시 |
| `pullModelsAsync(List)` | 병렬 비동기 다운로드 |
| `deleteModel(String)` | 모델 삭제 |

---

## 빠른 시작

### 1. 간단한 모델 다운로드

```java
@Service
@RequiredArgsConstructor
public class ModelService {

    private final SuhAiderEngine engine;

    public void downloadModel() {
        // 간단한 동기 다운로드 (완료까지 블로킹)
        boolean success = engine.pullModel("llama3.2");

        if (success) {
            System.out.println("다운로드 완료!");
        }
    }
}
```

### 2. 진행률 표시와 함께 다운로드

```java
public void downloadWithProgress() {
    PullHandle handle = engine.pullModelStream("llama3.2:70b", new PullCallback() {
        @Override
        public void onProgress(PullProgress progress) {
            System.out.printf("다운로드 중: %s\n", progress.getFormattedProgress());
            // 출력 예: "다운로드 중: 1.23 GB / 4.00 GB (30.8%)"
        }

        @Override
        public void onComplete(PullResult result) {
            if (result.isSuccess()) {
                System.out.println("완료! 소요시간: " + result.getFormattedDuration());
            }
        }
    });
}
```

### 3. 모델 삭제

```java
public void deleteModel() {
    boolean deleted = engine.deleteModel("llama3.2");

    if (deleted) {
        System.out.println("모델 삭제 완료!");
    }
}
```

---

## 모델 다운로드 (Pull)

### 방법 1: 동기 방식 (간단)

가장 단순한 방식입니다. 다운로드가 완료될 때까지 블로킹됩니다.

```java
// 기본 사용
boolean success = engine.pullModel("gemma3:4b");

// insecure 옵션 (HTTP 레지스트리 사용 시)
boolean success = engine.pullModel("my-model", true);
```

**주의**: 대용량 모델의 경우 수십 분이 걸릴 수 있습니다.

### 방법 2: 스트리밍 방식 (진행률 + 취소)

실시간 진행률을 받고, 중간에 취소할 수 있는 방식입니다.

```java
PullHandle handle = engine.pullModelStream("llama3.2:70b", new PullCallback() {
    @Override
    public void onProgress(PullProgress progress) {
        // 상태 확인
        System.out.println("상태: " + progress.getStatus());

        // 다운로드 중인 경우 진행률 표시
        if (progress.isDownloading()) {
            System.out.printf("진행률: %.1f%% (%s / %s)\n",
                progress.getPercent(),
                formatBytes(progress.getCompleted()),
                formatBytes(progress.getTotal()));
        }
    }

    @Override
    public void onComplete(PullResult result) {
        if (result.isSuccess()) {
            System.out.println("성공! 소요시간: " + result.getFormattedDuration());
        } else if (result.isCancelled()) {
            System.out.println("취소됨");
        } else {
            System.out.println("실패: " + result.getErrorMessage());
        }
    }
});

// 다운로드 취소 (필요시)
handle.cancel();
```

#### PullHandle 활용

```java
PullHandle handle = engine.pullModelStream("llama3.2", callback);

// 상태 확인
String modelName = handle.getModelName();           // "llama3.2"
boolean cancelled = handle.isCancelled();           // 취소 여부
boolean done = handle.isDone();                     // 완료 여부
PullProgress latest = handle.getLatestProgress();   // 최신 진행률

// 취소
if (userRequestedCancel) {
    handle.cancel();  // HTTP 연결 즉시 종료
}
```

### 방법 3: 비동기 방식 (논블로킹)

CompletableFuture를 반환하여 논블로킹으로 처리합니다.

```java
// 기본 비동기
CompletableFuture<PullResult> future = engine.pullModelAsync("llama3.2");

// 다른 작업 수행...

// 결과 확인
PullResult result = future.get();
if (result.isSuccess()) {
    System.out.println("완료: " + result.getModelName());
}
```

```java
// 진행률 리스너와 함께
CompletableFuture<PullResult> future = engine.pullModelAsync(
    "llama3.2",
    progress -> System.out.printf("진행률: %.1f%%\n", progress.getPercent())
);

// 콜백 체이닝
future.thenAccept(result -> {
    System.out.println("완료: " + result.getModelName());
}).exceptionally(ex -> {
    System.err.println("실패: " + ex.getMessage());
    return null;
});
```

### 방법 4: 병렬 다운로드

여러 모델을 동시에 다운로드합니다.

```java
// 병렬 스트리밍 방식
List<PullHandle> handles = engine.pullModelsParallel(
    List.of("llama3.2", "mistral", "codellama"),
    new PullCallback() {
        @Override
        public void onProgress(PullProgress progress) {
            // 모든 모델의 진행률이 여기로 옴
            System.out.println(progress.getStatus());
        }

        @Override
        public void onComplete(PullResult result) {
            System.out.println(result.getModelName() + ": " +
                (result.isSuccess() ? "완료" : "실패"));
        }
    }
);

// 특정 모델만 취소
handles.get(0).cancel();
```

```java
// 병렬 비동기 방식
CompletableFuture<List<PullResult>> future = engine.pullModelsAsync(
    List.of("llama3.2", "mistral")
);

List<PullResult> results = future.get();
results.forEach(r ->
    System.out.println(r.getModelName() + ": " + r.isSuccess()));
```

---

## 모델 삭제 (Delete)

### 기본 삭제

```java
// 삭제 (존재 확인 후)
boolean deleted = engine.deleteModel("llama3.2");

// 존재 확인 없이 삭제 시도
boolean deleted = engine.deleteModel("llama3.2", false);
```

### 조건부 삭제

```java
// 모델이 존재하는지 먼저 확인
List<ModelInfo> models = engine.getAvailableModels();
boolean exists = models.stream()
    .anyMatch(m -> m.getName().equals("llama3.2"));

if (exists) {
    engine.deleteModel("llama3.2");
}
```

---

## 고급 사용법

### 다운로드 후 즉시 사용

```java
public String generateAfterDownload(String modelName, String prompt) {
    // 모델이 없으면 다운로드
    if (!isModelAvailable(modelName)) {
        System.out.println("모델 다운로드 중...");
        boolean success = engine.pullModel(modelName);
        if (!success) {
            throw new RuntimeException("모델 다운로드 실패");
        }
    }

    // 텍스트 생성
    return engine.generate(modelName, prompt);
}

private boolean isModelAvailable(String modelName) {
    return engine.getAvailableModels().stream()
        .anyMatch(m -> m.getName().startsWith(modelName));
}
```

### 타임아웃 처리

```java
CompletableFuture<PullResult> future = engine.pullModelAsync("llama3.2:70b");

try {
    // 1시간 타임아웃
    PullResult result = future.get(1, TimeUnit.HOURS);
} catch (TimeoutException e) {
    System.out.println("다운로드 타임아웃");
}
```

### Spring WebFlux 통합 (SSE)

```java
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final SuhAiderEngine engine;

    @GetMapping(value = "/pull/{modelName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PullProgress> pullModel(@PathVariable String modelName) {
        return Flux.create(sink -> {
            engine.pullModelStream(modelName, new PullCallback() {
                @Override
                public void onProgress(PullProgress progress) {
                    sink.next(progress);
                }

                @Override
                public void onComplete(PullResult result) {
                    sink.complete();
                }
            });
        });
    }
}
```

---

## 트러블슈팅

### 다운로드 실패: TIMEOUT

**증상**: 다운로드 시작 전 타임아웃

**원인**: AI 서버 연결 문제

**해결**:
```yaml
suh:
  aider:
    connect-timeout: 30  # 연결 타임아웃 증가
```

### 다운로드 실패: HTTP/2 INTERNAL_ERROR

**증상**: 다운로드 중간에 `stream was reset: INTERNAL_ERROR` 발생

**원인**: 서버 측 HTTP/2 스트림 리셋 (네트워크 불안정, 서버 부하 등)

**해결**:
```java
// 재시도 로직 추가
int maxRetries = 3;
for (int i = 0; i < maxRetries; i++) {
    try {
        boolean success = engine.pullModel(modelName);
        if (success) break;
    } catch (Exception e) {
        if (i == maxRetries - 1) throw e;
        Thread.sleep(5000);  // 5초 대기 후 재시도
    }
}
```

### 삭제 실패: MODEL_NOT_FOUND

**증상**: `MODEL_DELETE_FAILED` 에러 발생

**원인**: 존재하지 않는 모델 삭제 시도

**해결**:
```java
// 존재 확인 없이 삭제 시도 (에러 무시)
try {
    engine.deleteModel("unknown-model", false);
} catch (SuhAiderException e) {
    // 무시
}
```

### 병렬 다운로드 시 리소스 부족

**증상**: 여러 모델 동시 다운로드 시 느려짐 또는 실패

**원인**: 네트워크 대역폭, 디스크 I/O 한계

**해결**:
```java
// 순차적으로 다운로드
for (String model : modelList) {
    engine.pullModel(model);
}

// 또는 2개씩 제한
Semaphore semaphore = new Semaphore(2);
modelList.parallelStream().forEach(model -> {
    try {
        semaphore.acquire();
        engine.pullModel(model);
    } finally {
        semaphore.release();
    }
});
```

---

## DTO 참조

### PullProgress

다운로드 진행 상태를 담는 DTO입니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `status` | String | 현재 상태 ("pulling manifest", "downloading", "success" 등) |
| `digest` | String | 다운로드 중인 레이어 해시 |
| `completed` | long | 완료된 바이트 수 |
| `total` | long | 전체 바이트 수 |

| 메서드 | 반환 | 설명 |
|--------|------|------|
| `getPercent()` | double | 진행률 (0.0 ~ 100.0) |
| `getFormattedProgress()` | String | "1.2 GB / 4.0 GB (30.0%)" |
| `isDownloading()` | boolean | 다운로드 중인지 |
| `isSuccess()` | boolean | 성공 상태인지 |

### PullResult

다운로드 결과를 담는 DTO입니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `modelName` | String | 모델명 |
| `success` | boolean | 성공 여부 |
| `cancelled` | boolean | 취소 여부 |
| `totalDurationMs` | long | 소요 시간 (밀리초) |
| `errorMessage` | String | 에러 메시지 |

| 메서드 | 반환 | 설명 |
|--------|------|------|
| `getFormattedDuration()` | String | "2분 30초" |
| `success(modelName, durationMs)` | PullResult | 성공 결과 생성 |
| `cancelled(modelName)` | PullResult | 취소 결과 생성 |
| `failure(modelName, error)` | PullResult | 실패 결과 생성 |

### PullCallback

진행률 콜백 인터페이스입니다.

| 메서드 | 설명 |
|--------|------|
| `onProgress(PullProgress)` | 진행 상태 업데이트 시 호출 |
| `onComplete(PullResult)` | 완료 시 호출 (성공/실패/취소) |

> **v2.0 변경**: `onError`가 제거됐습니다. 성공·실패·취소 모두 `onComplete(PullResult)`로
> **정확히 한 번** 전달됩니다. 예외 객체는 `result.getCause()`로 확인하세요.

### PullHandle

다운로드 제어 인터페이스입니다.

| 메서드 | 반환 | 설명 |
|--------|------|------|
| `cancel()` | void | 다운로드 취소 |
| `isCancelled()` | boolean | 취소 여부 |
| `isDone()` | boolean | 완료 여부 |
| `getLatestProgress()` | PullProgress | 최신 진행률 |
| `getModelName()` | String | 모델명 |

---

## 관련 문서

- [README.md](../README.md) - 전체 기능 개요
- [Function Calling 가이드](FUNCTION_CALLING_GUIDE.md) - 의도 분류 기능
- [JSON Schema 가이드](JSON_SCHEMA_GUIDE.md) - 구조화된 응답 생성
- [Ollama API 문서](https://github.com/ollama/ollama/blob/main/docs/api.md) - 공식 API 문서
