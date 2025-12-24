# Function Calling 기능 가이드

> **v0.3.0+**: FunctionGemma 등 Function Calling 지원 모델로 사용자 의도를 분류하는 기능

---

## 📋 목차

- [개요](#개요)
- [주요 기능](#주요-기능)
- [빠른 시작](#빠른-시작)
- [Tool 정의 방법](#tool-정의-방법)
- [사용 예제](#사용-예제)
- [고급 사용법](#고급-사용법)
- [트러블슈팅](#트러블슈팅)

---

## 개요

**문제**: RAG 기반 챗봇에서 모든 사용자 질문에 대해 벡터 검색을 수행하면 불필요한 리소스가 소모됩니다. "안녕하세요", "서버 상태 알려줘" 같은 질문은 RAG가 필요 없습니다.

**해결**: Function Calling을 통해 사용자 의도를 먼저 분류하고, 필요한 경우에만 RAG 검색을 수행합니다.

### 작동 원리

```
사용자 입력: "SSE 설정 어디서 했지?"
        ↓
FunctionGemma (270M, 경량 모델)
        ↓
Tool 선택: route_rag, arguments: {query: "SSE 설정"}
        ↓
분기 처리: RAG 검색 수행
```

### 왜 FunctionGemma인가?

| 특징 | 설명 |
|------|------|
| **경량 모델** | 270M 파라미터로 빠른 추론 |
| **Function Calling 특화** | Tool 선택에 최적화된 학습 |
| **낮은 리소스** | CPU에서도 실행 가능 |
| **Ollama 지원** | `ollama pull functiongemma`로 설치 |

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **완전 커스텀 Tool** | 기본 Tool 없이 모든 Tool 직접 정의 |
| **Fluent Builder 패턴** | 템플릿 정의 후 재사용 가능 |
| **다양한 파라미터 타입** | string, integer, boolean, enum 지원 |
| **인자 추출 헬퍼** | 타입 안전한 인자 추출 메서드 |
| **DB 연동 가능** | 런타임에 동적으로 Tool 구성 가능 |

---

## 빠른 시작

### 1. 모델 설치

```bash
ollama pull functiongemma
```

### 2. 기본 사용법

```java
import kr.suhsaechan.ai.model.FunctionRequest;
import kr.suhsaechan.ai.model.FunctionResponse;
import kr.suhsaechan.ai.model.FunctionTool;

@Service
@RequiredArgsConstructor
public class ChatRouter {

    private final SuhAiderEngine engine;

    public void route(String userMessage) {
        // 1. Tool 정의
        FunctionTool ragTool = FunctionTool.of("route_rag",
            "Use when user asks about configuration or documentation",
            "query", "string", "Search query for RAG");

        FunctionTool systemTool = FunctionTool.of("route_system",
            "Use when user asks about server status or logs");

        FunctionTool smalltalkTool = FunctionTool.of("route_smalltalk",
            "Use for greetings, chitchat, or casual conversation");

        // 2. 요청 생성
        FunctionRequest request = FunctionRequest.builder()
            .model("functiongemma")
            .systemPrompt("You are a strict router. Choose exactly ONE tool call based on user intent.")
            .tool(ragTool)
            .tool(systemTool)
            .tool(smalltalkTool)
            .userText(userMessage)
            .build();

        // 3. 호출
        FunctionResponse response = engine.functionCall(request);

        // 4. 분기 처리
        if (response.isHasToolCall()) {
            switch (response.getToolName()) {
                case "route_rag":
                    String query = response.getArgumentAsString("query");
                    handleRagSearch(query);
                    break;
                case "route_system":
                    handleSystemQuery();
                    break;
                case "route_smalltalk":
                    handleSmalltalk(userMessage);
                    break;
            }
        } else {
            // Tool 선택 실패 - clarify 처리
            handleClarify(userMessage);
        }
    }
}
```

---

## Tool 정의 방법

### 방법 1: 정적 팩토리 (가장 간단)

```java
// 파라미터 없는 Tool
FunctionTool tool = FunctionTool.of("route_smalltalk", "Use for greetings");

// 단일 파라미터 Tool
FunctionTool tool = FunctionTool.of(
    "route_rag",
    "Use for RAG search",
    "query", "string", "Search query"
);
```

### 방법 2: 빌더 패턴 (상세 설정)

```java
FunctionTool tool = FunctionTool.builder()
    .name("route_system")
    .description("Use for system operations like status check or log retrieval")
    .parameters(List.of(
        FunctionTool.FunctionParameter.required("query", "string", "The query text"),
        FunctionTool.FunctionParameter.optional("limit", "integer", "Max results"),
        FunctionTool.FunctionParameter.enumType("action", "Action type",
            "get_status", "get_logs", "check_port")
    ))
    .build();
```

### 방법 3: 체이닝 (동적 추가)

```java
FunctionTool tool = FunctionTool.builder()
    .name("search")
    .description("Search documents")
    .build();

tool.addParameter("query", "string", "Search query", true);
tool.addParameter("limit", "integer", "Max results", false);
```

### 파라미터 타입

| 타입 | 설명 | 예시 |
|------|------|------|
| `string` | 문자열 | `"query"`, `"name"` |
| `integer` | 정수 | `"limit"`, `"page"` |
| `number` | 실수 | `"threshold"`, `"score"` |
| `boolean` | 불리언 | `"includeArchived"` |
| `enum` | 제한된 값 | `"action": ["get", "set"]` |

---

## 사용 예제

### 예제 1: RAG 라우터

```java
@Component
public class RagRouter {

    private final SuhAiderEngine engine;
    private final FunctionRequest.FunctionRequestBuilder routerTemplate;

    public RagRouter(SuhAiderEngine engine) {
        this.engine = engine;

        // 템플릿 한 번 정의
        this.routerTemplate = FunctionRequest.builder()
            .model("functiongemma")
            .systemPrompt("""
                You are a strict router for a developer chatbot.
                Choose exactly ONE tool call based on user intent.

                Rules:
                - route_rag: Questions about code, configuration, documentation
                - route_system: Server status, logs, port check
                - route_smalltalk: Greetings, thanks, casual chat
                """)
            .tool(FunctionTool.of("route_rag", "Code/config questions",
                "query", "string", "Search query"))
            .tool(FunctionTool.of("route_system", "Server operations",
                "action", "string", "Action type"))
            .tool(FunctionTool.of("route_smalltalk", "Casual conversation"));
    }

    public RouteResult route(String userMessage) {
        FunctionResponse response = engine.functionCall(
            routerTemplate.userText(userMessage).build()
        );

        return RouteResult.builder()
            .toolName(response.getToolName())
            .query(response.getArgumentAsString("query"))
            .action(response.getArgumentAsString("action"))
            .hasToolCall(response.isHasToolCall())
            .build();
    }
}
```

### 예제 2: DB 기반 동적 Tool

```java
@Service
@RequiredArgsConstructor
public class DynamicRouter {

    private final SuhAiderEngine engine;
    private final ToolRepository toolRepository;

    public FunctionResponse route(String userMessage) {
        // DB에서 Tool 목록 조회
        List<ToolEntity> toolEntities = toolRepository.findAllActive();

        // FunctionTool로 변환
        List<FunctionTool> tools = toolEntities.stream()
            .map(this::toFunctionTool)
            .collect(Collectors.toList());

        // 요청 생성
        FunctionRequest.FunctionRequestBuilder builder = FunctionRequest.builder()
            .model("functiongemma")
            .systemPrompt("Choose the most appropriate tool.")
            .userText(userMessage);

        tools.forEach(builder::tool);

        return engine.functionCall(builder.build());
    }

    private FunctionTool toFunctionTool(ToolEntity entity) {
        FunctionTool.FunctionToolBuilder builder = FunctionTool.builder()
            .name(entity.getName())
            .description(entity.getDescription());

        if (entity.getParameters() != null) {
            List<FunctionTool.FunctionParameter> params = entity.getParameters().stream()
                .map(p -> FunctionTool.FunctionParameter.builder()
                    .name(p.getName())
                    .type(p.getType())
                    .description(p.getDescription())
                    .required(p.isRequired())
                    .build())
                .collect(Collectors.toList());
            builder.parameters(params);
        }

        return builder.build();
    }
}
```

### 예제 3: Enum 파라미터 활용

```java
FunctionTool systemTool = FunctionTool.builder()
    .name("system_action")
    .description("Perform system operations")
    .parameters(List.of(
        FunctionTool.FunctionParameter.enumType("action", "Action to perform",
            "get_status",      // 서버 상태 조회
            "get_logs",        // 로그 조회
            "check_port",      // 포트 확인
            "restart_service"  // 서비스 재시작
        ),
        FunctionTool.FunctionParameter.optional("service", "string", "Service name")
    ))
    .build();

// 응답 처리
FunctionResponse response = engine.functionCall(request);
if ("system_action".equals(response.getToolName())) {
    String action = response.getArgumentAsString("action");
    String service = response.getArgumentAsString("service");

    switch (action) {
        case "get_status":
            return getServerStatus();
        case "get_logs":
            return getLogs(service);
        case "check_port":
            return checkPort();
        case "restart_service":
            return restartService(service);
    }
}
```

---

## 고급 사용법

### 인자 추출 메서드

```java
FunctionResponse response = engine.functionCall(request);

// String
String query = response.getArgumentAsString("query");

// Integer
Integer limit = response.getArgumentAsInteger("limit");

// Boolean
Boolean includeArchived = response.getArgumentAsBoolean("includeArchived");

// List
List<Object> tags = response.getArgumentAsList("tags");

// 존재 여부 확인
if (response.hasArgument("limit")) {
    // limit 파라미터가 존재
}
```

### 원본 응답 디버깅

```java
FunctionResponse response = engine.functionCall(request);

// 원본 ChatResponse 접근
ChatResponse rawResponse = response.getRawResponse();

// 전체 tool_calls 확인
if (rawResponse.hasToolCalls()) {
    List<ChatMessage.ToolCall> toolCalls = rawResponse.getMessage().getToolCalls();
    toolCalls.forEach(tc -> {
        System.out.println("Tool: " + tc.getFunction().getName());
        System.out.println("Args: " + tc.getFunction().getArguments());
    });
}
```

### 모델 옵션 설정

```java
FunctionRequest request = FunctionRequest.builder()
    .model("functiongemma")
    .systemPrompt("...")
    .userText("...")
    .tool(tool)
    .options(Map.of(
        "temperature", 0.0,  // 결정적 출력
        "top_k", 1           // 가장 확실한 선택
    ))
    .keepAlive("10m")  // 모델 메모리 유지
    .build();
```

---

## 트러블슈팅

### Tool이 선택되지 않음

**증상**: `response.isHasToolCall()`이 `false`

**원인**:
1. systemPrompt가 너무 모호함
2. Tool description이 사용자 질문과 맞지 않음
3. 모델이 어떤 Tool도 적합하지 않다고 판단

**해결**:
```java
// 1. 명확한 systemPrompt
.systemPrompt("""
    You MUST choose exactly ONE tool call.
    If unsure, choose route_clarify.
    NEVER respond with text, only tool calls.
    """)

// 2. 폴백 Tool 추가
.tool(FunctionTool.of("route_clarify",
    "Use when user intent is unclear or doesn't match other tools"))
```

### 잘못된 Tool이 선택됨

**증상**: 예상과 다른 Tool이 선택됨

**해결**:
1. Tool description을 더 구체적으로 작성
2. 예시를 systemPrompt에 추가

```java
.systemPrompt("""
    Choose ONE tool based on user intent:

    - route_rag: "SSE 설정 어디?", "코드 어디있어?", "설정 방법"
    - route_system: "서버 상태", "로그 보여줘", "포트 확인"
    - route_smalltalk: "안녕", "고마워", "ㅋㅋ"
    """)
```

### Arguments 파싱 실패

**증상**: `getArgumentAsString()` 등이 `null` 반환

**원인**:
1. 파라미터 이름 불일치
2. 모델이 arguments를 생성하지 않음

**해결**:
```java
// 파라미터 존재 여부 확인
if (response.hasArgument("query")) {
    String query = response.getArgumentAsString("query");
} else {
    // 기본값 사용 또는 에러 처리
}

// 원본 arguments 확인 (디버깅)
Map<String, Object> args = response.getArguments();
System.out.println("Arguments: " + args);
```

### 네트워크 타임아웃

**증상**: Function Calling이 타임아웃됨

**해결**:
```yaml
# application.yml
suh:
  aider:
    read-timeout: 180  # 3분으로 증가
```

---

## 관련 문서

- [README.md](../README.md) - 전체 기능 개요
- [JSON Schema 가이드](JSON_SCHEMA_GUIDE.md) - 구조화된 응답 생성
- [Ollama Tool Calling 문서](https://github.com/ollama/ollama/blob/main/docs/api.md#chat) - 공식 API 문서
