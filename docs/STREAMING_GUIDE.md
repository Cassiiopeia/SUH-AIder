# 스트리밍 응답 기능 가이드

> **v0.1.0+**: ChatGPT처럼 실시간 토큰 단위 응답 스트리밍 지원


> **v2.0 변경**
>
> - 스트리밍에서도 `responseSchema`가 동작합니다. 스키마가 Ollama 네이티브 `format`으로
>   전달되므로 조각을 모두 이어 붙이면 유효한 JSON이 됩니다.
>   (v1.x는 경고만 남기고 무시했습니다.)
> - `generateStreamAsync` / `chatStreamAsync`는 전용 스레드풀에서 실행됩니다.
>   공용 ForkJoinPool을 점유하지 않아 애플리케이션의 다른 비동기 작업에 영향을 주지 않습니다.
> - 스트리밍 종료 통지(`onComplete` 또는 `onError`)는 어떤 경로로 끝나든 정확히 한 번 호출됩니다.

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [빠른 시작](#빠른-시작)
- [Generate 스트리밍](#generate-스트리밍)
- [Chat 스트리밍](#chat-스트리밍)
- [웹 통합](#웹-통합)
- [고급 사용법](#고급-사용법)
- [트러블슈팅](#트러블슈팅)

---

## 개요

스트리밍 응답은 AI가 생성하는 텍스트를 토큰 단위로 실시간 전달받는 기능입니다. ChatGPT처럼 글자가 하나씩 나타나는 UX를 구현할 수 있습니다.

### 스트리밍 vs 일반 응답

| 방식 | 특징 | 적합한 경우 |
|------|------|-------------|
| **일반 응답** | 전체 응답 완성 후 한 번에 반환 | 백엔드 처리, 짧은 응답 |
| **스트리밍** | 토큰마다 실시간 전달 | 긴 응답, 실시간 UX |

```
일반 응답:
[요청] ─── 3초 대기 ─── [전체 응답]

스트리밍:
[요청] → 안 → 녕 → 하 → 세 → 요 → ! → [완료]
        ↑    ↑    ↑    ↑    ↑    ↑
       0.1s 0.2s 0.3s 0.4s 0.5s 0.6s
```

---

## 주요 기능

| 메서드 | 설명 |
|--------|------|
| `generateStream(model, prompt, callback)` | Generate API 스트리밍 |
| `generateStream(request, callback)` | 상세 옵션으로 스트리밍 |
| `generateStreamAsync(...)` | 비동기 스트리밍 |
| `chatStream(model, messages, callback)` | Chat API 스트리밍 |
| `chatStream(request, callback)` | 상세 옵션으로 Chat 스트리밍 |
| `chatStreamAsync(...)` | 비동기 Chat 스트리밍 |

---

## 빠른 시작

### 1. 기본 스트리밍

```java
@Service
@RequiredArgsConstructor
public class StreamingService {

    private final SuhAiderEngine engine;

    public void streamExample() {
        engine.generateStream("gemma3:4b", "안녕하세요!", new StreamCallback() {
            @Override
            public void onNext(String chunk) {
                // 토큰마다 호출 (실시간)
                System.out.print(chunk);
            }

            @Override
            public void onComplete() {
                // 응답 완료
                System.out.println("\n--- 완료 ---");
            }

            @Override
            public void onError(Throwable error) {
                // 에러 처리
                System.err.println("에러: " + error.getMessage());
            }
        });
    }
}
```

### 2. StringBuilder로 전체 응답 수집

```java
public String streamAndCollect(String prompt) {
    StringBuilder fullResponse = new StringBuilder();
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
        @Override
        public void onNext(String chunk) {
            fullResponse.append(chunk);
            System.out.print(chunk);  // 실시간 출력도 가능
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            errorRef.set(error);
            latch.countDown();
        }
    });

    try {
        latch.await();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    if (errorRef.get() != null) {
        throw new RuntimeException(errorRef.get());
    }

    return fullResponse.toString();
}
```

---

## Generate 스트리밍

### 간편 사용

```java
engine.generateStream("gemma3:4b", "Hello, AI!", new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        System.out.print(chunk);
    }

    @Override
    public void onComplete() {
        System.out.println();
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

### 상세 옵션 사용

```java
SuhAiderRequest request = SuhAiderRequest.builder()
    .model("gemma3:4b")
    .prompt("다음 코드를 설명해주세요:\n\n" + code)
    .systemPrompt("You are a helpful coding assistant.")
    .stream(true)  // 스트리밍 활성화 (자동 설정됨)
    .options(Map.of(
        "temperature", 0.7,
        "top_p", 0.9
    ))
    .build();

engine.generateStream(request, callback);
```

### 비동기 스트리밍

```java
CompletableFuture<Void> future = engine.generateStreamAsync(
    "gemma3:4b",
    "긴 이야기를 작성해주세요.",
    callback
);

// 다른 작업 수행...

// 완료 대기 (필요시)
future.join();
```

---

## Chat 스트리밍

대화형 Chat API도 스트리밍을 지원합니다.

### 기본 사용

```java
List<ChatMessage> messages = List.of(
    ChatMessage.system("You are a helpful assistant."),
    ChatMessage.user("안녕하세요!")
);

engine.chatStream("gemma3:4b", messages, new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        System.out.print(chunk);
    }

    @Override
    public void onComplete() {
        System.out.println();
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

### 상세 옵션

```java
ChatRequest request = ChatRequest.builder()
    .model("gemma3:4b")
    .messages(messages)
    .stream(true)
    .options(Map.of("temperature", 0.8))
    .build();

engine.chatStream(request, callback);
```

### 비동기 Chat 스트리밍

```java
CompletableFuture<Void> future = engine.chatStreamAsync(request, callback);
```

---

## 웹 통합

### Spring WebFlux (SSE)

Server-Sent Events를 통해 브라우저로 실시간 스트리밍합니다.

```java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class StreamingController {

    private final SuhAiderEngine engine;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String prompt) {
        return Flux.create(sink -> {
            engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
                @Override
                public void onNext(String chunk) {
                    sink.next(chunk);
                }

                @Override
                public void onComplete() {
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        });
    }
}
```

### Spring MVC (SseEmitter)

```java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class SseController {

    private final SuhAiderEngine engine;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam String prompt) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executor.execute(() -> {
            try {
                engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
                    @Override
                    public void onNext(String chunk) {
                        try {
                            emitter.send(SseEmitter.event()
                                .data(chunk)
                                .name("message"));
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
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

### 프론트엔드 (JavaScript)

```javascript
// EventSource 사용
const eventSource = new EventSource('/api/ai/stream?prompt=안녕하세요');

eventSource.onmessage = (event) => {
    document.getElementById('output').textContent += event.data;
};

eventSource.onerror = (error) => {
    console.error('Error:', error);
    eventSource.close();
};

// 완료 시 (서버에서 close 이벤트 전송 필요)
eventSource.addEventListener('close', () => {
    eventSource.close();
});
```

```javascript
// fetch API 사용 (더 세밀한 제어)
async function streamChat(prompt) {
    const response = await fetch('/api/ai/stream?prompt=' + encodeURIComponent(prompt));
    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value);
        document.getElementById('output').textContent += chunk;
    }
}
```

---

## 고급 사용법

### 토큰 카운팅

```java
AtomicInteger tokenCount = new AtomicInteger(0);
StringBuilder response = new StringBuilder();

engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        tokenCount.incrementAndGet();
        response.append(chunk);
        System.out.print(chunk);
    }

    @Override
    public void onComplete() {
        System.out.println();
        System.out.println("총 토큰 수: " + tokenCount.get());
        System.out.println("총 문자 수: " + response.length());
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

### 타임아웃 처리

```java
CompletableFuture<Void> future = engine.generateStreamAsync(model, prompt, callback);

try {
    future.get(5, TimeUnit.MINUTES);  // 5분 타임아웃
} catch (TimeoutException e) {
    System.out.println("응답 시간 초과");
}
```

### 조건부 중단

스트리밍 중 특정 조건에서 중단하려면 예외를 발생시킵니다.

```java
AtomicInteger charCount = new AtomicInteger(0);
int maxChars = 1000;

engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
    @Override
    public void onNext(String chunk) {
        int currentCount = charCount.addAndGet(chunk.length());
        System.out.print(chunk);

        if (currentCount > maxChars) {
            throw new RuntimeException("최대 문자 수 초과");
        }
    }

    @Override
    public void onComplete() {
        System.out.println("\n완료");
    }

    @Override
    public void onError(Throwable error) {
        if (error.getMessage().contains("최대 문자 수")) {
            System.out.println("\n[중단됨: " + charCount.get() + "자]");
        } else {
            error.printStackTrace();
        }
    }
});
```

### 여러 스트림 병렬 처리

```java
List<String> prompts = List.of("질문1", "질문2", "질문3");
List<CompletableFuture<String>> futures = new ArrayList<>();

for (String prompt : prompts) {
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        StringBuilder sb = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        engine.generateStream("gemma3:4b", prompt, new StreamCallback() {
            @Override
            public void onNext(String chunk) {
                sb.append(chunk);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return sb.toString();
    });

    futures.add(future);
}

// 모든 응답 수집
List<String> responses = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

---

## 트러블슈팅

### 스트리밍이 느림

**증상**: 토큰 간 간격이 김

**원인**: 모델 로드 시간, 네트워크 지연

**해결**:
```yaml
suh:
  aider:
    model:
      keep-alive: 10m  # 모델 메모리 유지 시간 증가
```

### 중간에 끊김

**증상**: 응답이 불완전하게 종료

**원인**: 타임아웃, 네트워크 문제

**해결**:
```yaml
suh:
  aider:
    read-timeout: 300  # 5분으로 증가
```

### 브라우저에서 SSE 연결 끊김

**증상**: EventSource가 자동으로 닫힘

**원인**: 프록시 타임아웃, 브라우저 제한

**해결**:
```java
// 주기적으로 heartbeat 전송
@Scheduled(fixedRate = 15000)
public void sendHeartbeat() {
    activeEmitters.forEach(emitter -> {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException e) {
            // 연결 끊어진 emitter 제거
        }
    });
}
```

### 메모리 누수 (많은 동시 스트림)

**증상**: 메모리 사용량 증가

**원인**: SseEmitter, Flux 등이 제대로 정리되지 않음

**해결**:
```java
@GetMapping("/stream")
public SseEmitter stream(@RequestParam String prompt) {
    SseEmitter emitter = new SseEmitter(60000L);  // 1분 타임아웃

    emitter.onCompletion(() -> {
        // 리소스 정리
        activeEmitters.remove(emitter);
    });

    emitter.onTimeout(() -> {
        emitter.complete();
        activeEmitters.remove(emitter);
    });

    activeEmitters.add(emitter);
    // ...
}
```

---

## DTO 참조

### StreamCallback

스트리밍 응답 콜백 인터페이스입니다.

| 메서드 | 설명 |
|--------|------|
| `onNext(String chunk)` | 토큰(청크)이 도착할 때마다 호출 |
| `onComplete()` | 스트림 완료 시 호출 |
| `onError(Throwable error)` | 에러 발생 시 호출 |

### 관련 요청 DTO

**SuhAiderRequest** (Generate용):

| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | String | 모델명 |
| `prompt` | String | 프롬프트 |
| `systemPrompt` | String | 시스템 프롬프트 |
| `stream` | Boolean | 스트리밍 활성화 (자동 true) |
| `options` | Map | 모델 옵션 |

**ChatRequest** (Chat용):

| 필드 | 타입 | 설명 |
|------|------|------|
| `model` | String | 모델명 |
| `messages` | List<ChatMessage> | 대화 메시지 |
| `stream` | Boolean | 스트리밍 활성화 |
| `options` | Map | 모델 옵션 |

---

## 관련 문서

- [README.md](../README.md) - 전체 기능 개요
- [Function Calling 가이드](FUNCTION_CALLING_GUIDE.md) - 의도 분류 기능
- [JSON Schema 가이드](JSON_SCHEMA_GUIDE.md) - 구조화된 응답 생성
- [임베딩 가이드](EMBEDDING_GUIDE.md) - 텍스트 임베딩
- [모델 관리 가이드](MODEL_MANAGEMENT_GUIDE.md) - 모델 다운로드/삭제
